package com.healthcare.kallo.dto.response.food;

import com.healthcare.kallo.model.food.enums.FoodSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record IngredientResponse(
    UUID id,
    String code,
    String name,
    BigDecimal caloriesPer100g,
    BigDecimal proteinPer100g,
    BigDecimal carbsPer100g,
    BigDecimal fatPer100g,
    BigDecimal sugarPer100g,
    BigDecimal fiberPer100g,
    boolean isAllergen,
    FoodSource source,
    UUID ownerUserId,
    boolean isActive,
    Instant createdAt,
    Instant updatedAt
) {}
