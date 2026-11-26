package com.healthcare.kallo.service.plan;

import com.healthcare.kallo.common.PageResponse;
import com.healthcare.kallo.dto.request.plan.GenerateMealPlanRequest;
import com.healthcare.kallo.dto.request.plan.SwapMealPlanItemRequest;
import com.healthcare.kallo.dto.response.plan.MealPlanResponse;
import com.healthcare.kallo.dto.response.plan.MealPlanSummaryResponse;
import java.time.LocalDate;
import java.util.UUID;

/** Service managing personalized meal plan generation, lifecycle, item swapping, and logging. */
public interface MealPlanService {

  MealPlanResponse generate(UUID userId, GenerateMealPlanRequest request);

  MealPlanResponse save(UUID userId, UUID mealPlanId);

  PageResponse<MealPlanSummaryResponse> list(UUID userId, int page, int size);

  MealPlanResponse getById(UUID userId, UUID mealPlanId);

  void delete(UUID userId, UUID mealPlanId);

  MealPlanResponse swapItem(
      UUID userId, UUID mealPlanId, UUID itemId, SwapMealPlanItemRequest request);

  MealPlanResponse apply(UUID userId, UUID mealPlanId, LocalDate date, Integer dayIndex);
}
