package com.healthcare.kallo.dto.request.nutri;

import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.model.nutri.enums.FoodLogSource;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CreateFoodLogRequest(
    @NotNull UUID foodId,
    @NotNull MealType mealType,
    String servingName,
    @Positive BigDecimal servingGrams,
    @Positive BigDecimal servingQuantity,
    FoodLogSource source,
    @NotNull LocalDate loggedDate,
    Instant loggedAt,
    @NotNull UUID clientUuid,
    String imageUrl,
    Integer aiPredictedClassId
) {}
