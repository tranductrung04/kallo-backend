package com.healthcare.kallo.service.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
import com.healthcare.kallo.service.report.impl.DailySummaryServiceImpl;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class DailySummaryServiceTest {

  @Mock private DailySummaryRepository dailySummaryRepository;
  @Mock private UserFoodLogRepository userFoodLogRepository;
  @Mock private WaterLogRepository waterLogRepository;
  @Mock private UserHealthProfileRepository userHealthProfileRepository;
  @Mock private UserRepository userRepository;
  @Mock private ObjectProvider<ActivityDailyAggregator> activityDailyAggregatorProvider;
  @Mock private ActivityDailyAggregator activityDailyAggregator;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private UserTimeResolver timeResolver;

  private DailySummaryService dailySummaryService;

  private UUID userId;
  private User user;

  @BeforeEach
  void setUp() {
    dailySummaryService = new DailySummaryServiceImpl(
        dailySummaryRepository,
        userFoodLogRepository,
        waterLogRepository,
        userHealthProfileRepository,
        userRepository,
        activityDailyAggregatorProvider,
        eventPublisher,
        timeResolver
    );

    userId = UUID.randomUUID();
    user = new User();
    user.setId(userId);
    user.setTimezone("Asia/Ho_Chi_Minh");
  }

  @Test
  @DisplayName("recalculate() calculates totals, updates daily summary, and emits events")
  void recalculate_success() {
    LocalDate date = LocalDate.of(2026, 11, 20);

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    // Food nutrient totals: [cal, pro, carb, fat, sugar, fiber]
    Object[] sums = new Object[] {
        new BigDecimal("1850.0"),
        new BigDecimal("120.0"),
        new BigDecimal("210.0"),
        new BigDecimal("55.0"),
        new BigDecimal("25.0"),
        new BigDecimal("30.0")
    };
    when(userFoodLogRepository.sumNutrientsByDate(userId, date)).thenReturn(java.util.Collections.singletonList(sums));
    when(userFoodLogRepository.countDistinctMealTypes(userId, date)).thenReturn(3);

    // Water totals
    when(waterLogRepository.sumWaterMlByDate(userId, date)).thenReturn(1800);

    // User profile goals
    UserHealthProfile profile = new UserHealthProfile();
    profile.setDailyCalorieGoal(1900);
    profile.setWaterGoalMl(2000);
    when(userHealthProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

    // Activity stats
    when(activityDailyAggregatorProvider.getIfAvailable()).thenReturn(activityDailyAggregator);
    DailyActivityStats stats = new DailyActivityStats(
        date, 8500L, new BigDecimal("350.0"), new BigDecimal("150.0"),
        new BigDecimal("500.0"), 45, new BigDecimal("75.0"), new BigDecimal("130.0"),
        new BigDecimal("68.5"), "HEALTH_CONNECT"
    );
    when(activityDailyAggregator.aggregate(userId, date)).thenReturn(stats);

    when(dailySummaryRepository.findByUserIdAndSummaryDate(userId, date)).thenReturn(Optional.empty());

    dailySummaryService.recalculate(userId, date);

    ArgumentCaptor<DailySummary> summaryCaptor = ArgumentCaptor.forClass(DailySummary.class);
    verify(dailySummaryRepository).save(summaryCaptor.capture());

    DailySummary saved = summaryCaptor.getValue();
    assertThat(saved.getCaloriesConsumed()).isEqualTo(1850);
    assertThat(saved.getTargetCalories()).isEqualTo(1900);
    assertThat(saved.getWaterMl()).isEqualTo(1800);
    assertThat(saved.getTargetWaterMl()).isEqualTo(2000);
    assertThat(saved.getCaloriesBurned()).isEqualTo(500);
    assertThat(saved.getSteps()).isEqualTo(8500);
    assertThat(saved.getWeightKg()).isEqualByComparingTo(new BigDecimal("68.5"));
    assertThat(saved.getMealsLoggedCount()).isEqualTo(3);
    assertThat(saved.isGoalAchieved()).isTrue(); // |1850 - 1900| = 50 <= 190, meals >= 2

    verify(eventPublisher).publishEvent(any(DailySummaryChangedEvent.class));
    verify(eventPublisher).publishEvent(any(DailyProgressChangedEvent.class));
  }

  @Test
  @DisplayName("getTodaySummary() resolves timezone and returns daily summary")
  void getTodaySummary_success() {
    LocalDate today = LocalDate.of(2026, 11, 20);
    when(timeResolver.zoneOf(userId)).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));

    DailySummary summary = new DailySummary();
    summary.setUser(user);
    summary.setSummaryDate(today);
    summary.setCaloriesConsumed(1500);
    summary.setTargetCalories(2000);
    summary.setWaterMl(1200);

    when(dailySummaryRepository.findByUserIdAndSummaryDate(eq(userId), any(LocalDate.class)))
        .thenReturn(Optional.of(summary));

    DailySummaryResponse response = dailySummaryService.getTodaySummary(userId);

    assertThat(response).isNotNull();
    assertThat(response.caloriesConsumed()).isEqualTo(1500);
    assertThat(response.caloriesRemaining()).isEqualTo(500);
    assertThat(response.waterMl()).isEqualTo(1200);
  }
}
