package com.healthcare.kallo.dto.response.report;

import java.time.LocalDate;
import java.util.List;

public record CaloriesReportResponse(
    String period,
    LocalDate startDate,
    LocalDate endDate,
    int averageCaloriesConsumed,
    int averageCaloriesBurned,
    int totalDaysTracked,
    int daysGoalMetCount,
    List<Item> timeSeries
) {
  public record Item(
      LocalDate date,
      int caloriesConsumed,
      int caloriesBurned,
      int targetCalories,
      int netCalories,
      boolean goalAchieved,
      boolean hasData
  ) {}
}
