package com.healthcare.kallo.dto.request.nutri;

import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.model.nutri.enums.LogSource;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CreateFoodLogRequest(
    @NotNull UUID foodId,
    @NotNull MealType mealType,
    @DecimalMin(value = "0.1") BigDecimal servingGrams,
    @DecimalMin(value = "0.01") BigDecimal servingQuantity,
    LogSource source,
    LocalDate loggedDate,
    Instant loggedAt,
    @NotNull UUID clientUuid,
    String tempImageKey,
    @Min(0) @Max(67) Integer aiPredictedClassId) {}
