package com.healthcare.kallo.dto.response.report;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DashboardReportResponse(
    LocalDate reportDate,
    CalorieStats calories,
    MacroStats macros,
    WaterStats water,
    ActivityStats activity,
    GoalStats goalStatus
) {
  public record CalorieStats(
      int consumed,
      int target,
      int burned,
      int netCalories,
      int remaining,
      String status,
      double percentageOfGoal
  ) {}

  public record MacroStats(
      NutrientProgress protein,
      NutrientProgress carbs,
      NutrientProgress fat,
      SugarProgress sugar,
      FiberProgress fiber
  ) {}

  public record NutrientProgress(
      BigDecimal consumedG,
      BigDecimal targetG,
      double percentage
  ) {}

  public record SugarProgress(
      BigDecimal consumedG,
      boolean limitExceeded
  ) {}

  public record FiberProgress(
      BigDecimal consumedG
  ) {}

  public record WaterStats(
      int consumedMl,
      int targetMl,
      double percentage
  ) {}

  public record ActivityStats(
      int steps,
      int caloriesBurned,
      BigDecimal currentWeightKg
  ) {}

  public record GoalStats(
      boolean calorieGoalMet,
      boolean sugarExceeded,
      boolean proteinExceeded,
      int mealsLoggedCount
  ) {}
}
