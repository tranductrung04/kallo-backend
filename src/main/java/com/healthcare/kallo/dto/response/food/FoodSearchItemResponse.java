package com.healthcare.kallo.dto.response.food;

import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.model.food.enums.FoodSource;
import java.math.BigDecimal;
import java.util.UUID;

public record FoodSearchItemResponse(
    UUID id,
    String name,
    FoodCategory category,
    String defaultServingName,
    BigDecimal defaultServingGrams,
    Integer caloriesPerServing,
    BigDecimal proteinPerServing,
    BigDecimal carbsPerServing,
    BigDecimal fatPerServing,
    String imageUrl,
    FoodSource source,
    boolean isVerified
) {}
