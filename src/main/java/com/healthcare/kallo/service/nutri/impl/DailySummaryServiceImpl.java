package com.healthcare.kallo.service.nutri.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.NutriProperties;
import com.healthcare.kallo.dto.response.act.DailyActivityStats;
import com.healthcare.kallo.dto.response.nutri.DailySummaryResponse;
import com.healthcare.kallo.event.game.DailyProgressChangedEvent;
import com.healthcare.kallo.event.sys.DailySummaryChangedEvent;
import com.healthcare.kallo.model.nutri.DailySummary;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.repository.nutri.DailySummaryRepository;
import com.healthcare.kallo.repository.nutri.UserFoodLogRepository;
import com.healthcare.kallo.repository.nutri.WaterLogRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.act.ActivityDailyAggregator;
import com.healthcare.kallo.service.act.UserTimeResolver;
import com.healthcare.kallo.service.game.DailyGoalPolicy;
import com.healthcare.kallo.service.nutri.DailySummaryService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailySummaryServiceImpl implements DailySummaryService {

  private static final int MAX_RANGE_DAYS = 90;

  private final DailySummaryRepository dailySummaryRepository;
  private final UserFoodLogRepository userFoodLogRepository;
  private final WaterLogRepository waterLogRepository;
  private final UserHealthProfileRepository profileRepository;
  private final ActivityDailyAggregator activityDailyAggregator;
  private final UserTimeResolver userTimeResolver;
  private final DailyGoalPolicy dailyGoalPolicy;
  private final NutriProperties nutriProperties;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public DailySummaryServiceImpl(
      DailySummaryRepository dailySummaryRepository,
      UserFoodLogRepository userFoodLogRepository,
      WaterLogRepository waterLogRepository,
      UserHealthProfileRepository profileRepository,
      ActivityDailyAggregator activityDailyAggregator,
      UserTimeResolver userTimeResolver,
      DailyGoalPolicy dailyGoalPolicy,
      NutriProperties nutriProperties,
      ApplicationEventPublisher events,
      Clock clock) {
    this.dailySummaryRepository = dailySummaryRepository;
    this.userFoodLogRepository = userFoodLogRepository;
    this.waterLogRepository = waterLogRepository;
    this.profileRepository = profileRepository;
    this.activityDailyAggregator = activityDailyAggregator;
    this.userTimeResolver = userTimeResolver;
    this.dailyGoalPolicy = dailyGoalPolicy;
    this.nutriProperties = nutriProperties;
    this.events = events;
    this.clock = clock;
  }

  @Override
  @Transactional
  public DailySummaryResponse recalculate(UUID userId, LocalDate date) {
    if (userId == null || date == null) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
    userTimeResolver.zoneOf(userId);

    dailySummaryRepository.insertIfAbsent(userId, date);
    DailySummary summary =
        dailySummaryRepository
            .findByUserIdAndSummaryDateForUpdate(userId, date)
            .orElseThrow(() -> new BusinessException(ErrorCode.DAILY_SUMMARY_NOT_FOUND));

    UserFoodLogRepository.DailyFoodTotals foodTotals =
        userFoodLogRepository.aggregateDailyTotals(userId, date);
    int caloriesConsumed = scale0Int(foodTotals.getCalories());
    BigDecimal proteinG = scale1(foodTotals.getProteinG());
    BigDecimal carbsG = scale1(foodTotals.getCarbsG());
    BigDecimal fatG = scale1(foodTotals.getFatG());
    BigDecimal sugarG = scale1(foodTotals.getSugarG());
    BigDecimal fiberG = scale1(foodTotals.getFiberG());
    int mealsLoggedCount = Math.min(4, Math.max(0, foodTotals.getMealsLoggedCount()));

    int waterMl = Math.max(0, waterLogRepository.sumAmountMlByUserIdAndLoggedDate(userId, date));

    UserHealthProfile profile = profileRepository.findById(userId).orElse(null);
    Integer targetCalories = extractTargetCalories(profile);
    Integer targetWaterMl = extractTargetWaterMl(profile);
    boolean proteinExceeded = isProteinExceeded(profile, proteinG);

    DailyActivityStats activityStats = activityDailyAggregator.aggregate(userId, date);
    int steps = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, activityStats.totalSteps()));
    int caloriesBurned =
        activityStats.totalCaloriesBurned() != null
            ? Math.max(0, activityStats.totalCaloriesBurned().setScale(0, RoundingMode.HALF_UP).intValue())
            : 0;
    BigDecimal weightKg =
        activityStats.weightKg() != null
            ? activityStats.weightKg().setScale(1, RoundingMode.HALF_UP)
            : null;

    boolean goalAchieved =
        dailyGoalPolicy.isMet(mealsLoggedCount, caloriesConsumed, targetCalories);
    boolean sugarExceeded =
        sugarG.compareTo(nutriProperties.getSugarWarningThresholdG()) > 0;

    summary.setCaloriesConsumed(caloriesConsumed);
    summary.setTargetCalories(targetCalories);
    summary.setProteinG(proteinG);
    summary.setCarbsG(carbsG);
    summary.setFatG(fatG);
    summary.setSugarG(sugarG);
    summary.setFiberG(fiberG);
    summary.setWaterMl(waterMl);
    summary.setTargetWaterMl(targetWaterMl);
    summary.setCaloriesBurned(caloriesBurned);
    summary.setSteps(steps);
    summary.setWeightKg(weightKg);
    summary.setGoalAchieved(goalAchieved);
    summary.setSugarExceeded(sugarExceeded);
    summary.setProteinExceeded(proteinExceeded);
    summary.setMealsLoggedCount(mealsLoggedCount);

    DailySummary saved = dailySummaryRepository.saveAndFlush(summary);

    double goalForAlert = targetCalories != null ? targetCalories : 0.0;
    events.publishEvent(
        new DailySummaryChangedEvent(userId, date, caloriesConsumed, goalForAlert));
    events.publishEvent(new DailyProgressChangedEvent(userId, date));

    return toResponse(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public DailySummaryResponse getToday(UUID userId) {
    ZoneId zone = userTimeResolver.zoneOf(userId);
    LocalDate today = userTimeResolver.localDate(Instant.now(clock), zone);
    return getByDate(userId, today);
  }

  @Override
  @Transactional(readOnly = true)
  public DailySummaryResponse getByDate(UUID userId, LocalDate date) {
    if (date == null) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
    userTimeResolver.zoneOf(userId);
    return dailySummaryRepository
        .findByUserIdAndSummaryDate(userId, date)
        .map(this::toResponse)
        .orElseGet(() -> buildEmptySummaryResponse(userId, date));
  }

  @Override
  @Transactional(readOnly = true)
  public List<DailySummaryResponse> getRange(UUID userId, LocalDate from, LocalDate to) {
    if (from == null || to == null || from.isAfter(to)) {
      throw new BusinessException(ErrorCode.INVALID_TIME_RANGE);
    }
    if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
      throw new BusinessException(ErrorCode.INVALID_TIME_RANGE);
    }
    userTimeResolver.zoneOf(userId);
    return dailySummaryRepository
        .findByUserIdAndSummaryDateBetweenOrderBySummaryDateAsc(userId, from, to)
        .stream()
        .map(this::toResponse)
        .toList();
  }

  private DailySummaryResponse buildEmptySummaryResponse(UUID userId, LocalDate date) {
    UserHealthProfile profile = profileRepository.findById(userId).orElse(null);
    Integer targetCalories = extractTargetCalories(profile);
    Integer targetWaterMl = extractTargetWaterMl(profile);
    DailyActivityStats activityStats = activityDailyAggregator.aggregate(userId, date);
    int steps = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, activityStats.totalSteps()));
    int caloriesBurned =
        activityStats.totalCaloriesBurned() != null
            ? Math.max(0, activityStats.totalCaloriesBurned().setScale(0, RoundingMode.HALF_UP).intValue())
            : 0;
    BigDecimal weightKg =
        activityStats.weightKg() != null
            ? activityStats.weightKg().setScale(1, RoundingMode.HALF_UP)
            : null;
    return new DailySummaryResponse(
        date,
        0,
        targetCalories,
        targetCalories,
        BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP),
        BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP),
        BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP),
        BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP),
        BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP),
        0,
        targetWaterMl,
        caloriesBurned,
        steps,
        weightKg,
        false,
        false,
        false,
        0);
  }

  private Integer extractTargetCalories(UserHealthProfile profile) {
    if (profile == null
        || profile.getDailyCalorieGoal() == null
        || profile.getDailyCalorieGoal().compareTo(BigDecimal.ZERO) <= 0) {
      return null;
    }
    return profile.getDailyCalorieGoal().setScale(0, RoundingMode.HALF_UP).intValue();
  }

  private Integer extractTargetWaterMl(UserHealthProfile profile) {
    if (profile == null
        || profile.getWaterLiters() == null
        || profile.getWaterLiters().compareTo(BigDecimal.ZERO) <= 0) {
      return null;
    }
    return profile
        .getWaterLiters()
        .multiply(BigDecimal.valueOf(1000))
        .setScale(0, RoundingMode.HALF_UP)
        .intValue();
  }

  private boolean isProteinExceeded(UserHealthProfile profile, BigDecimal proteinG) {
    if (profile == null
        || profile.getProteinGrams() == null
        || profile.getProteinGrams().compareTo(BigDecimal.ZERO) <= 0) {
      return false;
    }
    BigDecimal limit = profile.getProteinGrams().multiply(nutriProperties.getProteinWarningRatio());
    return proteinG.compareTo(limit) > 0;
  }

  private int scale0Int(BigDecimal value) {
    if (value == null) return 0;
    return Math.max(0, value.setScale(0, RoundingMode.HALF_UP).intValue());
  }

  private BigDecimal scale1(BigDecimal value) {
    return (value == null ? BigDecimal.ZERO : value).setScale(1, RoundingMode.HALF_UP);
  }

  private DailySummaryResponse toResponse(DailySummary d) {
    Integer remaining =
        d.getTargetCalories() != null
            ? d.getTargetCalories() - d.getCaloriesConsumed()
            : null;
    return new DailySummaryResponse(
        d.getSummaryDate(),
        d.getCaloriesConsumed(),
        d.getTargetCalories(),
        remaining,
        scale1(d.getProteinG()),
        scale1(d.getCarbsG()),
        scale1(d.getFatG()),
        scale1(d.getSugarG()),
        scale1(d.getFiberG()),
        d.getWaterMl(),
        d.getTargetWaterMl(),
        d.getCaloriesBurned(),
        d.getSteps(),
        d.getWeightKg(),
        d.isGoalAchieved(),
        d.isSugarExceeded(),
        d.isProteinExceeded(),
        d.getMealsLoggedCount());
  }
}
