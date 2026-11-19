package com.healthcare.kallo.service.food;

import com.healthcare.kallo.common.PageResponse;
import com.healthcare.kallo.dto.request.food.CustomFoodRequest;
import com.healthcare.kallo.dto.response.food.FoodResponse;
import com.healthcare.kallo.dto.response.food.FoodSearchItemResponse;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface FoodService {

  PageResponse<FoodSearchItemResponse> searchFoods(
      UUID currentUserId, String query, FoodCategory category, Pageable pageable);

  FoodResponse getFoodById(UUID foodId, UUID currentUserId);

  FoodResponse getFoodByVietfoodClassId(int classId);

  FoodResponse createCustomFood(UUID currentUserId, CustomFoodRequest request);

  FoodResponse updateCustomFood(UUID currentUserId, UUID foodId, CustomFoodRequest request);

  void deleteCustomFood(UUID currentUserId, UUID foodId);
}
