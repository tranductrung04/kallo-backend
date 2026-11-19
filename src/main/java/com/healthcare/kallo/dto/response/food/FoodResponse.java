package com.healthcare.kallo.dto.response.food;

import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.model.food.enums.FoodSource;
import com.healthcare.kallo.model.food.enums.MealType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FoodResponse(
    UUID id,
    String name,
    String nameUnaccented,
    FoodCategory category,
    String defaultServingName,
    BigDecimal defaultServingGrams,
    BigDecimal caloriesPer100g,
    BigDecimal proteinPer100g,
    BigDecimal carbsPer100g,
    BigDecimal fatPer100g,
    BigDecimal sugarPer100g,
    BigDecimal fiberPer100g,
    int caloriesPerServing,
    BigDecimal proteinPerServing,
    BigDecimal carbsPerServing,
    BigDecimal fatPerServing,
    BigDecimal sugarPerServing,
    BigDecimal fiberPerServing,
    String imageUrl,
    String barcode,
    FoodSource source,
    Integer vietfoodClassId,
    List<String> allergens,
    List<MealType> suitableMealTypes,
    Integer referencePriceVnd,
    boolean isVerified,
    @Schema(description = "ID của chủ sở hữu nếu là món tùy chỉnh, null nếu là món hệ thống")
    UUID ownerUserId,
    Instant createdAt,
    Instant updatedAt
) {
  public static FoodResponse fromEntity(Food food) {
    if (food == null) {
      return null;
    }
    return new FoodResponse(
        food.getId(),
        food.getName(),
        food.getNameUnaccented(),
        food.getCategory(),
        food.getDefaultServingName(),
        food.getDefaultServingGrams(),
        food.getCaloriesPer100g(),
        food.getProteinPer100g(),
        food.getCarbsPer100g(),
        food.getFatPer100g(),
        food.getSugarPer100g(),
        food.getFiberPer100g(),
        food.getCaloriesPerServing(),
        food.getProteinPerServing(),
        food.getCarbsPerServing(),
        food.getFatPerServing(),
        food.getSugarPerServing(),
        food.getFiberPerServing(),
        food.getImageUrl(),
        food.getBarcode(),
        food.getSource(),
        food.getVietfoodClassId(),
        food.getAllergens() != null ? food.getAllergens() : List.of(),
        food.getSuitableMealTypes() != null ? food.getSuitableMealTypes() : List.of(),
        food.getReferencePriceVnd(),
        food.isVerified(),
        food.getOwner() != null ? food.getOwner().getId() : null,
        food.getCreatedAt(),
        food.getUpdatedAt()
    );
  }
}
