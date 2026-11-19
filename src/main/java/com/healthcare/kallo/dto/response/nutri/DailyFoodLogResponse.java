package com.healthcare.kallo.dto.response.nutri;

import com.healthcare.kallo.model.food.enums.MealType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DailyFoodLogResponse(
    LocalDate date,
    int targetCalories,
    BigDecimal totalCalories,
    BigDecimal totalProteinG,
    BigDecimal totalCarbsG,
    BigDecimal totalFatG,
    BigDecimal totalSugarG,
    BigDecimal totalFiberG,
    Map<MealType, List<FoodLogResponse>> meals
) {}
