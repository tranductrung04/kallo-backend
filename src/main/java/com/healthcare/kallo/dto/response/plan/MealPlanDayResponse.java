package com.healthcare.kallo.dto.response.plan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MealPlanDayResponse(
    int dayIndex,
    LocalDate mealDate,
    int targetCalories,
    int totalCalories,
    BigDecimal calorieDifferencePct,
    int estimatedCostVnd,
    List<MealPlanSlotResponse> meals) {}
