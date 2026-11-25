package com.healthcare.kallo.service.chat.context;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record NutritionHistoryContext(
    int days,
    BigDecimal averageCalories,
    BigDecimal averageProteinG,
    BigDecimal averageCarbsG,
    BigDecimal averageFatG,
    BigDecimal averageWeightKg,
    BigDecimal goalAdherenceRate,
    List<DailySummary> daily) {
  public NutritionHistoryContext {
    daily = daily == null ? List.of() : List.copyOf(daily);
  }

  public record DailySummary(
      LocalDate date,
      Integer calories,
      BigDecimal proteinG,
      BigDecimal carbsG,
      BigDecimal fatG,
      BigDecimal weightKg,
      boolean calorieGoalMet) {}
}
