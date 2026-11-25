package com.healthcare.kallo.service.food;

import com.healthcare.kallo.common.PageResponse;
import com.healthcare.kallo.dto.request.food.CustomFoodRequest;
import com.healthcare.kallo.dto.request.food.RecipeFoodRequest;
import com.healthcare.kallo.dto.response.food.FoodResponse;
import com.healthcare.kallo.dto.response.food.FoodSearchItemResponse;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import java.util.UUID;

public interface FoodService {

  PageResponse<FoodSearchItemResponse> searchFoods(UUID currentUserId, String q, FoodCategory category, int page, int size);

  FoodResponse getFoodById(UUID currentUserId, UUID foodId);

  FoodResponse createCustomFood(UUID currentUserId, CustomFoodRequest request);

  FoodResponse createRecipeFood(UUID currentUserId, RecipeFoodRequest request);

  FoodResponse updateCustomFood(UUID currentUserId, UUID foodId, CustomFoodRequest request);

  void deleteCustomFood(UUID currentUserId, UUID foodId);
}
