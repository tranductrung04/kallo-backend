package com.healthcare.kallo.service.plan;

import com.healthcare.kallo.dto.request.plan.GenerateMealPlanRequest;
import com.healthcare.kallo.dto.request.plan.SaveMealPlanRequest;
import com.healthcare.kallo.dto.request.plan.SwapFoodItemRequest;
import com.healthcare.kallo.dto.response.plan.ApplyMealPlanResponse;
import com.healthcare.kallo.dto.response.plan.MealPlanResponse;
import com.healthcare.kallo.model.plan.enums.MealPlanStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MealPlanService {

  MealPlanResponse generateMealPlan(UUID userId, GenerateMealPlanRequest request);

  MealPlanResponse saveMealPlan(UUID userId, UUID planId, SaveMealPlanRequest request);

  Page<MealPlanResponse> getMealPlans(UUID userId, MealPlanStatus status, Pageable pageable);

  MealPlanResponse getMealPlan(UUID userId, UUID planId);

  void deleteMealPlan(UUID userId, UUID planId);

  MealPlanResponse swapItem(UUID userId, UUID planId, UUID itemId, SwapFoodItemRequest request);

  ApplyMealPlanResponse applyMealPlan(UUID userId, UUID planId, LocalDate date);
}
