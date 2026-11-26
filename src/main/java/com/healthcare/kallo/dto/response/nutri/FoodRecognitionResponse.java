package com.healthcare.kallo.dto.response.nutri;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record FoodRecognitionResponse(
    String tempImageKey,
    String tempImageUrl,
    String modelVersion,
    List<RecognizedFoodItem> items) {

  public record BoundingBox(double x, double y, double w, double h) {}

  public record RecognizedFoodItem(
      Integer classId,
      String className,
      double confidence,
      BoundingBox bbox,
      UUID foodId,
      String foodName,
      String defaultServingName,
      BigDecimal defaultServingGrams,
      BigDecimal estimatedServingGrams,
      double sizeFactor,
      Integer caloriesPerServing,
      BigDecimal proteinPerServing,
      BigDecimal carbsPerServing,
      BigDecimal fatPerServing,
      boolean hasAllergyWarning,
      List<AllergicIngredientWarning> allergicIngredients) {}
}
