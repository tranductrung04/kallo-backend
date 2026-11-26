package com.healthcare.kallo.dto.request.nutri;

import com.healthcare.kallo.model.food.enums.MealType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public record UpdateFoodLogRequest(
    @NotNull MealType mealType,
    @DecimalMin(value = "0.1") BigDecimal servingGrams,
    @NotNull @DecimalMin(value = "0.01") BigDecimal servingQuantity,
    Instant clientUpdatedAt) {}
