package com.healthcare.kallo.dto.response.food;

import java.math.BigDecimal;
import java.util.UUID;

public record FoodIngredientItemResponse(
    UUID ingredientId,
    String ingredientName,
    BigDecimal quantityGrams,
    boolean isAllergen,
    BigDecimal caloriesPer100g
) {}
