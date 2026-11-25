package com.healthcare.kallo.dto.request.food;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RecipeItemRequest(
    @NotNull UUID ingredientId,
    @NotNull @DecimalMin("0.1") BigDecimal quantityGrams
) {}
