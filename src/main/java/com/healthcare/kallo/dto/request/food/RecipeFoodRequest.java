package com.healthcare.kallo.dto.request.food;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RecipeFoodRequest(
    @NotBlank @Size(max = 255) String name,
    @NotNull FoodCategory category,
    @NotBlank @Size(max = 100) String defaultServingName,
    @NotEmpty List<@Valid RecipeItemRequest> items,
    List<String> suitableMealTypes,
    String imageUrl
) {
  public RecipeFoodRequest {
    if (suitableMealTypes == null || suitableMealTypes.isEmpty()) {
      suitableMealTypes = List.of("BREAKFAST", "LUNCH", "DINNER");
    }
  }
}
