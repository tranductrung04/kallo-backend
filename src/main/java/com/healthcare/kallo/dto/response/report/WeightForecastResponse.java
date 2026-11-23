package com.healthcare.kallo.dto.response.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record WeightForecastResponse(
    String status,
    int dataPointsUsed,
    int minimumRequired,
    BigDecimal currentWeightKg,
    BigDecimal targetWeightKg,
    Integer tdee,
    Integer averageDailyNetDeficitKcal,
    BigDecimal projectedWeeklyChangeKg,
    String trend,
    LocalDate estimatedGoalDate,
    Integer daysRemainingToGoal,
    List<ProjectionPoint> projections,
    String recommendation
) {
  public record ProjectionPoint(
      int dayIndex,
      LocalDate date,
      BigDecimal projectedWeightKg
  ) {}
}
