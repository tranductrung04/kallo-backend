package com.healthcare.kallo.service.act.impl;

import com.healthcare.kallo.config.ActProperties;
import com.healthcare.kallo.dto.response.act.DailyActivityStats;
import com.healthcare.kallo.model.act.ActivityLog;
import com.healthcare.kallo.repository.act.ActivityLogRepository;
import com.healthcare.kallo.repository.act.WeightLogRepository;
import com.healthcare.kallo.service.act.ActivityDailyAggregator;
import com.healthcare.kallo.service.act.ProfileBodyMetrics;
import com.healthcare.kallo.service.act.StepCaloriesCalculator;
import com.healthcare.kallo.service.act.UserTimeResolver;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityDailyAggregatorImpl implements ActivityDailyAggregator {
  private final JdbcTemplate jdbc;
  private final ActivityLogRepository activities;
  private final WeightLogRepository weights;
  private final ObjectProvider<ProfileBodyMetrics> profile;
  private final UserTimeResolver timeResolver;
  private final ActProperties properties;

  public ActivityDailyAggregatorImpl(
      JdbcTemplate jdbc, ActivityLogRepository activities, WeightLogRepository weights,
      ObjectProvider<ProfileBodyMetrics> profile, UserTimeResolver timeResolver,
      ActProperties properties) {
    this.jdbc = jdbc;
    this.activities = activities;
    this.weights = weights;
    this.profile = profile;
    this.timeResolver = timeResolver;
    this.properties = properties;
  }

  @Override
  @Transactional(readOnly = true)
  public DailyActivityStats aggregate(UUID userId, LocalDate date) {
    ZoneId zone = timeResolver.zoneOf(userId);
    UserTimeResolver.DayRange range = timeResolver.dayRange(date, zone);
    List<StepBucket> steps = jdbc.query("""
        select provider, value, start_time, end_time
        from health_sync_logs
        where user_id = ? and data_type = 'STEPS'
          and start_time >= ? and start_time < ?
        order by start_time
        """, (rs, row) -> new StepBucket(rs.getString("provider"),
        rs.getBigDecimal("value").longValue(), rs.getTimestamp("start_time").toInstant(),
        rs.getTimestamp("end_time").toInstant()), userId,
        Timestamp.from(range.start()), Timestamp.from(range.end()));
    Map<String, StepBucket> selected = selectSteps(steps, zone);
    long totalSteps = selected.values().stream().mapToLong(StepBucket::steps).sum();

    List<HeartRate> heartRates = jdbc.query("""
        select value, value_max
        from health_sync_logs
        where user_id = ? and data_type = 'HEART_RATE'
          and start_time >= ? and start_time < ?
        """, (rs, row) -> new HeartRate(rs.getBigDecimal("value"), rs.getBigDecimal("value_max")),
        userId, Timestamp.from(range.start()), Timestamp.from(range.end()));
    List<ActivityLog> active = activities.findStartedInRange(userId, range.start(), range.end());
    List<ActivityLog> overlapping = activities.findActiveOverlapping(userId, range.start(), range.end());
    BigDecimal activityCalories = active.stream().map(ActivityLog::getCaloriesBurned)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    int activeMinutes = active.stream().mapToInt(ActivityLog::getDurationMin).sum();
    BigDecimal weight = weights.findByUserIdAndLoggedAtInRange(userId, range.start(), range.end())
        .stream().max(Comparator.comparing(w -> w.getLoggedAt()))
        .map(w -> w.getWeightKg()).orElseGet(() ->
            profile.getIfAvailable() == null ? null : profile.getIfAvailable().weightKg(userId).orElse(null));
    BigDecimal calorieWeight = weights.findLatestAtOrBefore(userId, range.end().minusNanos(1))
        .map(w -> w.getWeightKg()).orElseGet(() -> weight);
    BigDecimal height = profile.getIfAvailable() == null ? null : profile.getIfAvailable().heightCm(userId).orElse(null);
    BigDecimal stepCalories = stepCalories(selected.values(), overlapping, calorieWeight, height);
    BigDecimal totalCalories = activityCalories.add(stepCalories).setScale(1, RoundingMode.HALF_UP);
    BigDecimal averageHeartRate = heartRates.isEmpty() ? null
        : heartRates.stream().map(HeartRate::value).reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(heartRates.size()), 1, RoundingMode.HALF_UP);
    BigDecimal maxHeartRate = heartRates.stream()
        .map(rate -> rate.max() == null ? rate.value() : rate.max())
        .max(Comparator.naturalOrder()).orElse(null);
    String source = selected.values().stream().map(StepBucket::provider)
        .max(Comparator.comparingInt(this::sourcePriority).thenComparing(String::compareTo))
        .orElse(null);
    return new DailyActivityStats(date, totalSteps, scale(activityCalories), scale(stepCalories),
        totalCalories, activeMinutes, averageHeartRate, maxHeartRate, weight, source);
  }

  private Map<String, StepBucket> selectSteps(List<StepBucket> buckets, ZoneId zone) {
    Map<String, List<StepBucket>> grouped = buckets.stream().collect(Collectors.groupingBy(
        bucket -> bucket.start().atZone(zone).toLocalDateTime().withMinute(0).withSecond(0).withNano(0).toString()));
    Map<String, StepBucket> selected = new LinkedHashMap<>();
    for (var entry : grouped.entrySet()) {
      selected.put(entry.getKey(), entry.getValue().stream()
          .max(Comparator.comparingInt(bucket -> sourcePriority(bucket.provider()))).orElseThrow());
    }
    return selected;
  }

  private BigDecimal stepCalories(Collection<StepBucket> buckets, List<ActivityLog> activities,
      BigDecimal weight, BigDecimal height) {
    if (weight == null) return BigDecimal.ZERO.setScale(1);
    BigDecimal steps = BigDecimal.ZERO;
    for (StepBucket bucket : buckets) {
      boolean covered = activities.stream().anyMatch(activity ->
          ("WALKING".equals(activity.getActivityType().getCategory())
              || "RUNNING".equals(activity.getActivityType().getCategory()))
              && bucket.start().isBefore(activity.getEndedAt())
              && bucket.end().isAfter(activity.getStartedAt()));
      if (!covered) steps = steps.add(BigDecimal.valueOf(bucket.steps()));
    }
    return StepCaloriesCalculator.calculate(steps.longValue(), weight, height,
        properties.walkingMet(), properties.walkingSpeedKmh(),
        properties.fallbackCadenceSpm(), properties.strideFactor());
  }

  private int sourcePriority(String provider) {
    for (int index = 0; index < properties.stepSourcePriority().size(); index++) {
      if (provider.equals(properties.stepSourcePriority().get(index))) {
        return properties.stepSourcePriority().size() - index;
      }
    }
    return 0;
  }

  private BigDecimal scale(BigDecimal value) {
    return value.setScale(1, RoundingMode.HALF_UP);
  }

  private record StepBucket(String provider, long steps, Instant start, Instant end) {}
  private record HeartRate(BigDecimal value, BigDecimal max) {}
}
