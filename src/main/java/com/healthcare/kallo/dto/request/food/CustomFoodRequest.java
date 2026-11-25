package com.healthcare.kallo.dto.request.food;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomFoodRequest(
    @NotBlank @Size(max = 255) String name,
    @NotNull FoodCategory category,
    @NotBlank @Size(max = 100) String defaultServingName,
    @NotNull @DecimalMin("1.0") BigDecimal defaultServingGrams,
    @NotNull @DecimalMin("0.0") BigDecimal caloriesPer100g,
    @NotNull @DecimalMin("0.0") BigDecimal proteinPer100g,
    @NotNull @DecimalMin("0.0") BigDecimal carbsPer100g,
    @NotNull @DecimalMin("0.0") BigDecimal fatPer100g,
    @DecimalMin("0.0") BigDecimal sugarPer100g,
    @DecimalMin("0.0") BigDecimal fiberPer100g,
    List<String> suitableMealTypes,
    String imageUrl
) {
  public CustomFoodRequest {
    if (sugarPer100g == null) sugarPer100g = BigDecimal.ZERO;
    if (fiberPer100g == null) fiberPer100g = BigDecimal.ZERO;
    if (suitableMealTypes == null || suitableMealTypes.isEmpty()) {
      suitableMealTypes = List.of("BREAKFAST", "LUNCH", "DINNER");
    }
  }
}
