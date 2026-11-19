package com.healthcare.kallo.dto.response.nutri;

import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.model.nutri.enums.FoodLogSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record FoodLogResponse(
    UUID id,
    UUID foodId,
    String foodName,
    MealType mealType,
    String servingName,
    BigDecimal servingGrams,
    BigDecimal servingQuantity,
    FoodLogSource source,
    LocalDate loggedDate,
    Instant loggedAt,
    UUID clientUuid,
    String imageUrl,
    BigDecimal calories,
    BigDecimal proteinG,
    BigDecimal carbsG,
    BigDecimal fatG,
    BigDecimal sugarG,
    BigDecimal fiberG,
    boolean hasAllergyWarning,
    List<String> allergyWarnings
) {}
