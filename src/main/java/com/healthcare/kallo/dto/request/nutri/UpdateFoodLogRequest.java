package com.healthcare.kallo.dto.request.nutri;

import com.healthcare.kallo.model.food.enums.MealType;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record UpdateFoodLogRequest(
    MealType mealType,
    String servingName,
    @Positive BigDecimal servingGrams,
    @Positive BigDecimal servingQuantity
) {}
