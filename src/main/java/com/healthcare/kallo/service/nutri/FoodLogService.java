package com.healthcare.kallo.service.nutri;

import com.healthcare.kallo.dto.request.nutri.CopyMealRequest;
import com.healthcare.kallo.dto.request.nutri.CreateFoodLogRequest;
import com.healthcare.kallo.dto.request.nutri.UpdateFoodLogRequest;
import com.healthcare.kallo.dto.response.food.FoodSearchItemResponse;
import com.healthcare.kallo.dto.response.nutri.DailyFoodLogResponse;
import com.healthcare.kallo.dto.response.nutri.FoodLogResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FoodLogService {

  FoodLogResponse logFood(UUID userId, CreateFoodLogRequest request);

  FoodLogResponse updateFoodLog(UUID userId, UUID logId, UpdateFoodLogRequest request);

  void deleteFoodLog(UUID userId, UUID logId);

  DailyFoodLogResponse getDailyFoodLogs(UUID userId, LocalDate date);

  FoodLogResponse getFoodLog(UUID userId, UUID logId);

  List<FoodSearchItemResponse> getRecentFoods(UUID userId, int limit);

  void copyMeals(UUID userId, CopyMealRequest request);
}
