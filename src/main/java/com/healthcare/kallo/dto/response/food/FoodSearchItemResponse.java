package com.healthcare.kallo.dto.response.food;

import com.healthcare.kallo.model.food.Food;
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
    int caloriesPerServing,
    BigDecimal proteinPerServing,
    BigDecimal carbsPerServing,
    BigDecimal fatPerServing,
    String imageUrl,
    String barcode,
    FoodSource source,
    boolean isVerified,
    boolean isCustom
) {
  public static FoodSearchItemResponse fromEntity(Food food) {
    if (food == null) {
      return null;
    }
    return new FoodSearchItemResponse(
        food.getId(),
        food.getName(),
        food.getCategory(),
        food.getDefaultServingName(),
        food.getDefaultServingGrams(),
        food.getCaloriesPerServing(),
        food.getProteinPerServing(),
        food.getCarbsPerServing(),
        food.getFatPerServing(),
        food.getImageUrl(),
        food.getBarcode(),
        food.getSource(),
        food.isVerified(),
        food.getOwner() != null
    );
  }
}
