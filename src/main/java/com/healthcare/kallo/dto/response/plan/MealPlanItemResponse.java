package com.healthcare.kallo.dto.response.plan;

import com.healthcare.kallo.model.food.enums.FoodSource;
import java.math.BigDecimal;
import java.util.UUID;

public record MealPlanItemResponse(
    UUID id,
    UUID foodId,
    String foodName,
    String foodImageUrl,
    FoodSource foodSource,
    String servingName,
    BigDecimal servingGrams,
    BigDecimal servingMultiplier,
    int calories,
    BigDecimal proteinG,
    BigDecimal carbsG,
    BigDecimal fatG,
    BigDecimal sugarG,
    BigDecimal fiberG,
    int estimatedPriceVnd) {}
