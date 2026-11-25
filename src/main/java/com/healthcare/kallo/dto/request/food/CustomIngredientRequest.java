package com.healthcare.kallo.dto.request.food;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomIngredientRequest(
    @NotBlank @Size(max = 255) String name,
    @NotNull @DecimalMin("0.0") @DecimalMax("950.0") BigDecimal caloriesPer100g,
    @NotNull @DecimalMin("0.0") BigDecimal proteinPer100g,
    @NotNull @DecimalMin("0.0") BigDecimal carbsPer100g,
    @NotNull @DecimalMin("0.0") BigDecimal fatPer100g,
    @DecimalMin("0.0") BigDecimal sugarPer100g,
    @DecimalMin("0.0") BigDecimal fiberPer100g
) {
  public CustomIngredientRequest {
    if (sugarPer100g == null) sugarPer100g = BigDecimal.ZERO;
    if (fiberPer100g == null) fiberPer100g = BigDecimal.ZERO;
  }
}
