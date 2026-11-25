package com.healthcare.kallo.service.food;

import com.healthcare.kallo.dto.request.food.CustomIngredientRequest;
import com.healthcare.kallo.dto.response.food.IngredientResponse;
import java.util.List;
import java.util.UUID;

public interface IngredientService {

  List<IngredientResponse> searchIngredients(UUID currentUserId, String q, boolean allergenOnly);

  IngredientResponse createCustomIngredient(UUID currentUserId, CustomIngredientRequest request);

  IngredientResponse updateCustomIngredient(UUID currentUserId, UUID ingredientId, CustomIngredientRequest request);

  void deleteCustomIngredient(UUID currentUserId, UUID ingredientId);
}
