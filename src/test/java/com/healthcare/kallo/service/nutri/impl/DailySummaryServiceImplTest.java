package com.healthcare.kallo.service.nutri.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class DailySummaryServiceImplTest {

  private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final Instant FIXED_NOW = Instant.parse("2026-11-25T05:00:00Z");

  @Mock private DailySummaryRepository dailySummaryRepository;
  @Mock private UserFoodLogRepository userFoodLogRepository;
  @Mock private WaterLogRepository waterLogRepository;
  @Mock private UserHealthProfileRepository profileRepository;
  @Mock private ActivityDailyAggregator activityDailyAggregator;
  @Mock private UserTimeResolver userTimeResolver;
  @Mock private ApplicationEventPublisher events;

  private DailyGoalPolicy dailyGoalPolicy;
  private NutriProperties nutriProperties;
  private DailySummaryServiceImpl service;

  private UUID userId;
  private LocalDate date;

  @BeforeEach
  void setUp() {
    dailyGoalPolicy = new DailyGoalPolicy(2, 10);
    nutriProperties = new NutriProperties();
    service =
        new DailySummaryServiceImpl(
            dailySummaryRepository,
            userFoodLogRepository,
            waterLogRepository,
            profileRepository,
            activityDailyAggregator,
            userTimeResolver,
            dailyGoalPolicy,
            nutriProperties,
            events,
            Clock.fixed(FIXED_NOW, VN_ZONE));
    userId = UUID.randomUUID();
    date = LocalDate.of(2026, 11, 25);
  }

  @Test
  void testGoalAchievedAlignsWithDailyGoalPolicy() {
    when(userTimeResolver.zoneOf(userId)).thenReturn(VN_ZONE);
    DailySummary lockedRow = new DailySummary();
    lockedRow.setUserId(userId);
    lockedRow.setSummaryDate(date);
    when(dailySummaryRepository.findByUserIdAndSummaryDateForUpdate(userId, date))
        .thenReturn(Optional.of(lockedRow));

    UserFoodLogRepository.DailyFoodTotals totals = mockFoodTotals(2000, 100, 200, 60, 20, 25, 1);
    when(userFoodLogRepository.aggregateDailyTotals(userId, date)).thenReturn(totals);
    when(waterLogRepository.sumAmountMlByUserIdAndLoggedDate(userId, date)).thenReturn(1500);

    UserHealthProfile profile = new UserHealthProfile();
    profile.setUserId(userId);
    profile.setDailyCalorieGoal(new BigDecimal("2000"));
    profile.setWaterLiters(new BigDecimal("2.0"));
    profile.setProteinGrams(new BigDecimal("120"));
    when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));
    when(activityDailyAggregator.aggregate(userId, date))
        .thenReturn(stats(date, 5000L, new BigDecimal("250.0"), new BigDecimal("65.0")));
    when(dailySummaryRepository.saveAndFlush(any(DailySummary.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    DailySummaryResponse response = service.recalculate(userId, date);

    assertThat(response.caloriesConsumed()).isEqualTo(2000);
    assertThat(response.targetCalories()).isEqualTo(2000);
    assertThat(response.remainingCalories()).isZero();
    assertThat(response.mealsLoggedCount()).isEqualTo(1);
    assertThat(response.goalAchieved()).isFalse();
    verify(events).publishEvent(any(DailySummaryChangedEvent.class));
    verify(events).publishEvent(any(DailyProgressChangedEvent.class));
  }

  @Test
  void testDailySummaryWhenUserHasNoProfile() {
    when(userTimeResolver.zoneOf(userId)).thenReturn(VN_ZONE);
    DailySummary lockedRow = new DailySummary();
    lockedRow.setUserId(userId);
    lockedRow.setSummaryDate(date);
    when(dailySummaryRepository.findByUserIdAndSummaryDateForUpdate(userId, date))
        .thenReturn(Optional.of(lockedRow));

    UserFoodLogRepository.DailyFoodTotals totals = mockFoodTotals(0, 0, 0, 0, 0, 0, 0);
    when(userFoodLogRepository.aggregateDailyTotals(userId, date)).thenReturn(totals);
    when(waterLogRepository.sumAmountMlByUserIdAndLoggedDate(userId, date)).thenReturn(250);
    when(profileRepository.findById(userId)).thenReturn(Optional.empty());
    when(activityDailyAggregator.aggregate(userId, date))
        .thenReturn(stats(date, 0L, BigDecimal.ZERO, null));
    when(dailySummaryRepository.saveAndFlush(any(DailySummary.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    DailySummaryResponse response = service.recalculate(userId, date);

    assertThat(response.waterMl()).isEqualTo(250);
    assertThat(response.targetCalories()).isNull();
    assertThat(response.remainingCalories()).isNull();
    assertThat(response.targetWaterMl()).isNull();
    assertThat(response.goalAchieved()).isFalse();
    assertThat(response.proteinExceeded()).isFalse();

    ArgumentCaptor<DailySummaryChangedEvent> eventCaptor =
        ArgumentCaptor.forClass(DailySummaryChangedEvent.class);
    verify(events).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().calorieGoal()).isZero();
  }

  @Test
  void flagsSugarAndProteinWarningsWhenThresholdsExceeded() {
    when(userTimeResolver.zoneOf(userId)).thenReturn(VN_ZONE);
    DailySummary lockedRow = new DailySummary();
    lockedRow.setUserId(userId);
    lockedRow.setSummaryDate(date);
    when(dailySummaryRepository.findByUserIdAndSummaryDateForUpdate(userId, date))
        .thenReturn(Optional.of(lockedRow));

    UserFoodLogRepository.DailyFoodTotals totals =
        mockFoodTotals(1950, 150, 180, 50, 55.5, 20, 2);
    when(userFoodLogRepository.aggregateDailyTotals(userId, date)).thenReturn(totals);
    when(waterLogRepository.sumAmountMlByUserIdAndLoggedDate(userId, date)).thenReturn(2000);

    UserHealthProfile profile = new UserHealthProfile();
    profile.setUserId(userId);
    profile.setDailyCalorieGoal(new BigDecimal("2000"));
    profile.setWaterLiters(new BigDecimal("2.0"));
    profile.setProteinGrams(new BigDecimal("100"));
    when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));
    when(activityDailyAggregator.aggregate(userId, date))
        .thenReturn(stats(date, 8200L, new BigDecimal("320"), null));
    when(dailySummaryRepository.saveAndFlush(any(DailySummary.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    DailySummaryResponse response = service.recalculate(userId, date);

    assertThat(response.goalAchieved()).isTrue();
    assertThat(response.sugarExceeded()).isTrue();
    assertThat(response.proteinExceeded()).isTrue();
  }

  @Test
  void getRangeRejectsInvalidOrOver90DayRange() {
    assertThatThrownBy(() -> service.getRange(userId, date, date.minusDays(1)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_TIME_RANGE);

    assertThatThrownBy(() -> service.getRange(userId, date.minusDays(90), date))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_TIME_RANGE);

    verify(dailySummaryRepository, never())
        .findByUserIdAndSummaryDateBetweenOrderBySummaryDateAsc(any(), any(), any());
  }

  @Test
  void getByDateReturnsEmptyFallbackWhenSummaryNotYetMaterialized() {
    when(userTimeResolver.zoneOf(userId)).thenReturn(VN_ZONE);
    when(dailySummaryRepository.findByUserIdAndSummaryDate(userId, date))
        .thenReturn(Optional.empty());
    UserHealthProfile profile = new UserHealthProfile();
    profile.setDailyCalorieGoal(new BigDecimal("1800"));
    profile.setWaterLiters(new BigDecimal("2.5"));
    when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));
    when(activityDailyAggregator.aggregate(userId, date))
        .thenReturn(stats(date, 1200L, new BigDecimal("45"), new BigDecimal("60")));

    DailySummaryResponse response = service.getByDate(userId, date);

    assertThat(response.caloriesConsumed()).isZero();
    assertThat(response.targetCalories()).isEqualTo(1800);
    assertThat(response.targetWaterMl()).isEqualTo(2500);
    assertThat(response.steps()).isEqualTo(1200);
    assertThat(response.goalAchieved()).isFalse();
  }

  private static DailyActivityStats stats(
      LocalDate date, long steps, BigDecimal burned, BigDecimal weightKg) {
    return new DailyActivityStats(
        date, steps, burned, BigDecimal.ZERO, burned, 0, null, null, weightKg, "HEALTH_CONNECT");
  }

  private UserFoodLogRepository.DailyFoodTotals mockFoodTotals(
      double calories,
      double protein,
      double carbs,
      double fat,
      double sugar,
      double fiber,
      int meals) {
    UserFoodLogRepository.DailyFoodTotals totals =
        mock(UserFoodLogRepository.DailyFoodTotals.class);
    when(totals.getCalories()).thenReturn(BigDecimal.valueOf(calories));
    when(totals.getProteinG()).thenReturn(BigDecimal.valueOf(protein));
    when(totals.getCarbsG()).thenReturn(BigDecimal.valueOf(carbs));
    when(totals.getFatG()).thenReturn(BigDecimal.valueOf(fat));
    when(totals.getSugarG()).thenReturn(BigDecimal.valueOf(sugar));
    when(totals.getFiberG()).thenReturn(BigDecimal.valueOf(fiber));
    when(totals.getMealsLoggedCount()).thenReturn(meals);
    return totals;
  }
}
