package com.healthcare.kallo.dto.response.nutri;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RecognizeFoodItemResponse(
    UUID foodId,
    String foodName,
    int classId,
    double confidence,
    String defaultServingName,
    BigDecimal defaultServingGrams,
    BigDecimal caloriesPerServing,
    BigDecimal proteinPerServing,
    BigDecimal carbsPerServing,
    BigDecimal fatPerServing,
    boolean hasAllergyWarning,
    List<String> allergyWarnings,
    List<Double> boundingBox
) {}
