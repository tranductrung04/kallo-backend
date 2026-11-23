package com.healthcare.kallo.dto.response.plan;

import com.healthcare.kallo.model.food.enums.MealType;
import java.math.BigDecimal;
import java.util.UUID;

public record MealPlanFoodItemResponse(
    UUID id,
    UUID foodId,
    String foodName,
    MealType mealType,
    String servingName,
    BigDecimal servingGrams,
    BigDecimal servingMultiplier,
    int calories,
    BigDecimal proteinG,
    BigDecimal carbsG,
    BigDecimal fatG,
    BigDecimal sugarG,
    BigDecimal fiberG,
    int estimatedPriceVnd,
    String imageUrl
) {}
