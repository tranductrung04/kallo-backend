package com.healthcare.kallo.service.report.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.kallo.dto.response.report.WeightForecastResponse;
import com.healthcare.kallo.model.profile.enums.GoalType;
import com.healthcare.kallo.model.report.DailySummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeightForecastServiceTest {

  private WeightForecastService service;

  @BeforeEach
  void setUp() {
    service = new WeightForecastService();
  }

  private DailySummary createSummary(LocalDate date, int consumed, int burned) {
    DailySummary summary = new DailySummary();
    summary.setSummaryDate(date);
    summary.setCaloriesConsumed(consumed);
    summary.setCaloriesBurned(burned);
    return summary;
  }

  @Test
  @DisplayName("calculateForecast returns INSUFFICIENT_DATA when data count is less than 14 days")
  void calculateForecast_insufficientData_returnsWarning() {
    List<DailySummary> summaries = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      summaries.add(createSummary(LocalDate.now().minusDays(i), 1800, 300));
    }

    WeightForecastResponse response = service.calculateForecast(
        2000, BigDecimal.valueOf(70.0), BigDecimal.valueOf(65.0), GoalType.LOSE_WEIGHT, summaries
    );

    assertThat(response.status()).isEqualTo("INSUFFICIENT_DATA");
    assertThat(response.dataPointsUsed()).isEqualTo(7);
    assertThat(response.minimumRequired()).isEqualTo(14);
    assertThat(response.projections()).isEmpty();
    assertThat(response.recommendation()).contains("7 ngày");
  }

  @Test
  @DisplayName("calculateForecast calculates EMA and 30-day projection accurately with 14+ days")
  void calculateForecast_sufficientData_accurateEmaAndProjection() {
    List<DailySummary> summaries = new ArrayList<>();
    // 14 days of constant 500 kcal net deficit: consumed 1800, tdee 2000, burned 300 -> net deficit = 1800 - 2000 - 300 = -500 kcal
    for (int i = 14; i >= 1; i--) {
      summaries.add(createSummary(LocalDate.now().minusDays(i), 1800, 300));
    }

    WeightForecastResponse response = service.calculateForecast(
        2000, BigDecimal.valueOf(70.0), BigDecimal.valueOf(65.0), GoalType.LOSE_WEIGHT, summaries
    );

    assertThat(response.status()).isEqualTo("SUFFICIENT_DATA");
    assertThat(response.dataPointsUsed()).isEqualTo(14);
    assertThat(response.averageDailyNetDeficitKcal()).isCloseTo(-500, org.assertj.core.data.Offset.offset(2));
    // -500 * 7 / 7700 = -0.45 kg/week
    assertThat(response.projectedWeeklyChangeKg()).isCloseTo(BigDecimal.valueOf(-0.45), org.assertj.core.data.Offset.offset(BigDecimal.valueOf(0.05)));
    assertThat(response.trend()).isEqualTo("ON_TRACK");
    assertThat(response.projections()).hasSize(30);
    assertThat(response.estimatedGoalDate()).isNotNull();
    assertThat(response.daysRemainingToGoal()).isPositive();
  }

  @Test
  @DisplayName("calculateForecast detects OFF_TRACK when losing weight but net calories are in surplus")
  void calculateForecast_offTrackDetected() {
    List<DailySummary> summaries = new ArrayList<>();
    // Surplus: consumed 2600, tdee 2000, burned 200 -> net = +400 kcal
    for (int i = 14; i >= 1; i--) {
      summaries.add(createSummary(LocalDate.now().minusDays(i), 2600, 200));
    }

    WeightForecastResponse response = service.calculateForecast(
        2000, BigDecimal.valueOf(70.0), BigDecimal.valueOf(65.0), GoalType.LOSE_WEIGHT, summaries
    );

    assertThat(response.status()).isEqualTo("SUFFICIENT_DATA");
    assertThat(response.trend()).isEqualTo("OFF_TRACK");
    assertThat(response.estimatedGoalDate()).isNull();
    assertThat(response.recommendation()).contains("chững lại");
  }
}
