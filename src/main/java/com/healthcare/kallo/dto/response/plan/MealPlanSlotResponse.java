package com.healthcare.kallo.dto.response.plan;

import com.healthcare.kallo.model.food.enums.MealType;
import java.util.List;

public record MealPlanSlotResponse(
    MealType mealType,
    int targetCalories,
    int totalCalories,
    List<MealPlanItemResponse> items) {}
