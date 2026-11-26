package com.healthcare.kallo.dto.response.nutri;

import com.healthcare.kallo.model.food.enums.MealType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DailyFoodLogsResponse(
    LocalDate date,
    Map<MealType, List<FoodLogResponse>> meals,
    int totalCalories,
    Integer targetCalories,
    Integer remainingCalories,
    BigDecimal totalProteinG,
    BigDecimal totalCarbsG,
    BigDecimal totalFatG,
    BigDecimal totalSugarG,
    BigDecimal totalFiberG,
    int mealsLoggedCount) {}
