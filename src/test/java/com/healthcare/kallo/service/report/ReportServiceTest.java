package com.healthcare.kallo.service.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.dto.response.report.CaloriesReportResponse;
import com.healthcare.kallo.dto.response.report.DashboardReportResponse;
import com.healthcare.kallo.dto.response.report.MacrosReportResponse;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.model.profile.enums.GoalType;
import com.healthcare.kallo.model.report.DailySummary;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.repository.report.DailySummaryRepository;
import com.healthcare.kallo.service.report.engine.WeightForecastService;
import com.healthcare.kallo.service.report.impl.ReportServiceImpl;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

  @Mock private DailySummaryRepository dailySummaryRepository;
  @Mock private UserHealthProfileRepository userHealthProfileRepository;
  @Mock private WeightForecastService weightForecastService;

  private ReportService reportService;
  private UUID userId;
  private UserHealthProfile profile;

  @BeforeEach
  void setUp() {
    reportService = new ReportServiceImpl(
        dailySummaryRepository,
        userHealthProfileRepository,
        weightForecastService
    );

    userId = UUID.randomUUID();
    profile = new UserHealthProfile();
    profile.setDailyCalorieGoal(2000);
    profile.setWaterGoalMl(2500);
    profile.setProteinGoalG(BigDecimal.valueOf(120));
    profile.setCarbsGoalG(BigDecimal.valueOf(250));
    profile.setFatGoalG(BigDecimal.valueOf(60));
    profile.setWeightKg(BigDecimal.valueOf(68.5));
    profile.setTargetWeightKg(BigDecimal.valueOf(65.0));
    profile.setTdee(2200);
    profile.setGoalType(GoalType.LOSE_WEIGHT);
  }

  @Test
  @DisplayName("getDashboard returns today's stats accurately")
  void getDashboard_success() {
    LocalDate today = LocalDate.now();
    DailySummary summary = new DailySummary();
    summary.setSummaryDate(today);
    summary.setCaloriesConsumed(1800);
    summary.setTargetCalories(2000);
    summary.setCaloriesBurned(300);
    summary.setProteinG(BigDecimal.valueOf(110));
    summary.setCarbsG(BigDecimal.valueOf(200));
    summary.setFatG(BigDecimal.valueOf(50));
    summary.setWaterMl(2000);
    summary.setTargetWaterMl(2500);
    summary.setSteps(7500);
    summary.setGoalAchieved(true);
    summary.setMealsLoggedCount(3);

    when(userHealthProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(dailySummaryRepository.findByUserIdAndSummaryDate(userId, today)).thenReturn(Optional.of(summary));

    DashboardReportResponse response = reportService.getDashboard(userId, today);

    assertThat(response).isNotNull();
    assertThat(response.calories().consumed()).isEqualTo(1800);
    assertThat(response.calories().netCalories()).isEqualTo(1500);
    assertThat(response.calories().status()).isEqualTo("DEFICIT");
    assertThat(response.water().consumedMl()).isEqualTo(2000);
    assertThat(response.activity().steps()).isEqualTo(7500);
    assertThat(response.goalStatus().calorieGoalMet()).isTrue();
  }

  @Test
  @DisplayName("getCaloriesReport scaffolds missing days with 0 values")
  void getCaloriesReport_dateScaffolding_fillsMissingDays() {
    LocalDate today = LocalDate.of(2026, 11, 23);
    LocalDate day1 = today.minusDays(6);

    DailySummary s1 = new DailySummary();
    s1.setSummaryDate(day1);
    s1.setCaloriesConsumed(1900);
    s1.setCaloriesBurned(200);
    s1.setTargetCalories(2000);
    s1.setGoalAchieved(true);

    when(userHealthProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(dailySummaryRepository.findByUserIdAndSummaryDateBetweenOrderBySummaryDateAsc(eq(userId), any(), eq(today)))
        .thenReturn(List.of(s1));

    CaloriesReportResponse response = reportService.getCaloriesReport(userId, "WEEK", today);

    assertThat(response).isNotNull();
    assertThat(response.timeSeries()).hasSize(7);
    // Day 1 has data
    assertThat(response.timeSeries().get(0).hasData()).isTrue();
    assertThat(response.timeSeries().get(0).caloriesConsumed()).isEqualTo(1900);
    // Day 2 has no data
    assertThat(response.timeSeries().get(1).hasData()).isFalse();
    assertThat(response.timeSeries().get(1).caloriesConsumed()).isZero();
  }
}
