package com.healthcare.kallo.service.report.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.response.act.DailyActivityStats;
import com.healthcare.kallo.dto.response.report.DailySummaryResponse;
import com.healthcare.kallo.event.game.DailyProgressChangedEvent;
import com.healthcare.kallo.event.sys.DailySummaryChangedEvent;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.model.report.DailySummary;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.nutri.UserFoodLogRepository;
import com.healthcare.kallo.repository.nutri.WaterLogRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.repository.report.DailySummaryRepository;
import com.healthcare.kallo.service.act.ActivityDailyAggregator;
import com.healthcare.kallo.service.act.UserTimeResolver;
import com.healthcare.kallo.service.report.DailySummaryService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailySummaryServiceImpl implements DailySummaryService {

  private static final Logger log = LoggerFactory.getLogger(DailySummaryServiceImpl.class);

  private final DailySummaryRepository dailySummaryRepository;
  private final UserFoodLogRepository userFoodLogRepository;
  private final WaterLogRepository waterLogRepository;
  private final UserHealthProfileRepository userHealthProfileRepository;
  private final UserRepository userRepository;
  private final ObjectProvider<ActivityDailyAggregator> activityDailyAggregator;
  private final ApplicationEventPublisher eventPublisher;
  private final UserTimeResolver timeResolver;

  public DailySummaryServiceImpl(
      DailySummaryRepository dailySummaryRepository,
      UserFoodLogRepository userFoodLogRepository,
      WaterLogRepository waterLogRepository,
      UserHealthProfileRepository userHealthProfileRepository,
      UserRepository userRepository,
      ObjectProvider<ActivityDailyAggregator> activityDailyAggregator,
      ApplicationEventPublisher eventPublisher,
      UserTimeResolver timeResolver) {
    this.dailySummaryRepository = dailySummaryRepository;
    this.userFoodLogRepository = userFoodLogRepository;
    this.waterLogRepository = waterLogRepository;
    this.userHealthProfileRepository = userHealthProfileRepository;
    this.userRepository = userRepository;
    this.activityDailyAggregator = activityDailyAggregator;
    this.eventPublisher = eventPublisher;
    this.timeResolver = timeResolver;
  }

  @Override
  @Transactional
  public void recalculate(UUID userId, LocalDate date) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    // 1. Food totals
    List<Object[]> nutrientSums = userFoodLogRepository.sumNutrientsByDate(userId, date);
    BigDecimal calIn = BigDecimal.ZERO;
    BigDecimal protein = BigDecimal.ZERO;
    BigDecimal carbs = BigDecimal.ZERO;
    BigDecimal fat = BigDecimal.ZERO;
    BigDecimal sugar = BigDecimal.ZERO;
    BigDecimal fiber = BigDecimal.ZERO;

    if (nutrientSums != null && !nutrientSums.isEmpty() && nutrientSums.get(0) != null) {
      Object[] row = nutrientSums.get(0);
      calIn = toBigDecimal(row[0]);
      protein = toBigDecimal(row[1]);
      carbs = toBigDecimal(row[2]);
      fat = toBigDecimal(row[3]);
      sugar = toBigDecimal(row[4]);
      fiber = toBigDecimal(row[5]);
    }

    int mealsCount = userFoodLogRepository.countDistinctMealTypes(userId, date);
    int caloriesConsumed = calIn.setScale(0, RoundingMode.HALF_UP).intValue();

    // 2. Water totals
    int waterMl = waterLogRepository.sumWaterMlByDate(userId, date);

    // 3. User targets from profile
    Optional<UserHealthProfile> profileOpt = userHealthProfileRepository.findByUserId(userId);
    int targetCalories = profileOpt.map(UserHealthProfile::getDailyCalorieGoal).orElse(2000);
    int targetWaterMl = profileOpt.map(UserHealthProfile::getWaterGoalMl).orElse(2000);

    // 4. Activity & Weight totals from ACT
    int caloriesBurned = 0;
    int steps = 0;
    BigDecimal weightKg = null;

    ActivityDailyAggregator aggregator = activityDailyAggregator.getIfAvailable();
    if (aggregator != null) {
      try {
        DailyActivityStats stats = aggregator.aggregate(userId, date);
        if (stats != null) {
          if (stats.totalCaloriesBurned() != null) {
            caloriesBurned = stats.totalCaloriesBurned().setScale(0, RoundingMode.HALF_UP).intValue();
          }
          steps = (int) stats.totalSteps();
          weightKg = stats.weightKg();
        }
      } catch (Exception ex) {
        log.warn("Failed to aggregate activity stats for user {} on date {}: {}", userId, date, ex.getMessage());
      }
    }

    // 5. Goal & Threshold evaluations
    boolean goalAchieved = mealsCount >= 2 && Math.abs(caloriesConsumed - targetCalories) <= Math.round(targetCalories * 0.10f);
    boolean sugarExceeded = targetCalories > 0 && sugar.doubleValue() > (targetCalories * 0.10 / 4.0);
    boolean proteinExceeded = targetCalories > 0 && protein.doubleValue() > (targetCalories * 0.35 / 4.0);

    // 6. UPSERT into daily_summaries
    DailySummary summary = dailySummaryRepository.findByUserIdAndSummaryDate(userId, date)
        .orElseGet(() -> {
          DailySummary s = new DailySummary();
          s.setUser(user);
          s.setSummaryDate(date);
          return s;
        });

    summary.setCaloriesConsumed(caloriesConsumed);
    summary.setTargetCalories(targetCalories);
    summary.setProteinG(protein);
    summary.setCarbsG(carbs);
    summary.setFatG(fat);
    summary.setSugarG(sugar);
    summary.setFiberG(fiber);
    summary.setWaterMl(waterMl);
    summary.setTargetWaterMl(targetWaterMl);
    summary.setCaloriesBurned(caloriesBurned);
    summary.setSteps(steps);
    summary.setWeightKg(weightKg);
    summary.setGoalAchieved(goalAchieved);
    summary.setSugarExceeded(sugarExceeded);
    summary.setProteinExceeded(proteinExceeded);
    summary.setMealsLoggedCount(mealsCount);

    dailySummaryRepository.save(summary);

    // 7. Publish domain events
    eventPublisher.publishEvent(new DailySummaryChangedEvent(userId, date, caloriesConsumed, targetCalories));
    eventPublisher.publishEvent(new DailyProgressChangedEvent(userId, date));
  }

  @Override
  @Transactional(readOnly = true)
  public DailySummaryResponse getSummary(UUID userId, LocalDate date) {
    DailySummary summary = dailySummaryRepository.findByUserIdAndSummaryDate(userId, date)
        .orElseGet(() -> createEmptySummary(userId, date));
    return toResponse(summary);
  }

  @Override
  @Transactional(readOnly = true)
  public DailySummaryResponse getTodaySummary(UUID userId) {
    ZoneId zone = timeResolver.zoneOf(userId);
    LocalDate today = LocalDate.now(zone);
    return getSummary(userId, today);
  }

  private DailySummary createEmptySummary(UUID userId, LocalDate date) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    Optional<UserHealthProfile> profileOpt = userHealthProfileRepository.findByUserId(userId);

    DailySummary s = new DailySummary();
    s.setUser(user);
    s.setSummaryDate(date);
    s.setTargetCalories(profileOpt.map(UserHealthProfile::getDailyCalorieGoal).orElse(2000));
    s.setTargetWaterMl(profileOpt.map(UserHealthProfile::getWaterGoalMl).orElse(2000));
    return s;
  }

  private DailySummaryResponse toResponse(DailySummary s) {
    int remaining = s.getTargetCalories() - s.getCaloriesConsumed();
    return new DailySummaryResponse(
        s.getId(),
        s.getSummaryDate(),
        s.getCaloriesConsumed(),
        s.getTargetCalories(),
        remaining,
        s.getProteinG(),
        s.getCarbsG(),
        s.getFatG(),
        s.getSugarG(),
        s.getFiberG(),
        s.getWaterMl(),
        s.getTargetWaterMl(),
        s.getCaloriesBurned(),
        s.getSteps(),
        s.getWeightKg(),
        s.isGoalAchieved(),
        s.isSugarExceeded(),
        s.isProteinExceeded(),
        s.getMealsLoggedCount()
    );
  }

  private BigDecimal toBigDecimal(Object obj) {
    if (obj == null) return BigDecimal.ZERO;
    if (obj instanceof BigDecimal bd) return bd;
    if (obj instanceof Number num) return BigDecimal.valueOf(num.doubleValue());
    try {
      return new BigDecimal(obj.toString());
    } catch (Exception e) {
      return BigDecimal.ZERO;
    }
  }
}
