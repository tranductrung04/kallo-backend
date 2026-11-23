package com.healthcare.kallo.controller.plan;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.request.plan.GenerateMealPlanRequest;
import com.healthcare.kallo.dto.request.plan.SaveMealPlanRequest;
import com.healthcare.kallo.dto.request.plan.SwapFoodItemRequest;
import com.healthcare.kallo.dto.response.plan.ApplyMealPlanResponse;
import com.healthcare.kallo.dto.response.plan.MealPlanResponse;
import com.healthcare.kallo.model.plan.enums.MealPlanStatus;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.plan.MealPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/meal-plans")
@Tag(name = "Meal Plans", description = "AI-assisted meal planning, vector preference matching, and dietary scheduling")
public class MealPlanController {

  private final MealPlanService mealPlanService;

  public MealPlanController(MealPlanService mealPlanService) {
    this.mealPlanService = mealPlanService;
  }

  @PostMapping("/generate")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Generate a meal plan", description = "Generates a personalized daily meal plan respecting allergies, medical conditions, and preferences.")
  public ApiResponse<MealPlanResponse> generateMealPlan(
      @Valid @RequestBody(required = false) GenerateMealPlanRequest request,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(mealPlanService.generateMealPlan(userId, request));
  }

  @PostMapping("/{id}/save")
  @Operation(summary = "Save a draft meal plan", description = "Saves a draft meal plan to user's saved plans repository.")
  public ApiResponse<MealPlanResponse> saveMealPlan(
      @PathVariable("id") UUID id,
      @Valid @RequestBody(required = false) SaveMealPlanRequest request,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(mealPlanService.saveMealPlan(userId, id, request));
  }

  @GetMapping
  @Operation(summary = "Get meal plans", description = "Retrieves paginated meal plans for the current user.")
  public ApiResponse<Page<MealPlanResponse>> getMealPlans(
      @RequestParam(name = "status", required = false) MealPlanStatus status,
      @PageableDefault(size = 10) Pageable pageable,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(mealPlanService.getMealPlans(userId, status, pageable));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get meal plan details", description = "Retrieves details of a specific meal plan including items and medical warnings.")
  public ApiResponse<MealPlanResponse> getMealPlan(
      @PathVariable("id") UUID id,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(mealPlanService.getMealPlan(userId, id));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete meal plan", description = "Deletes a meal plan belonging to the current user.")
  public ApiResponse<Void> deleteMealPlan(
      @PathVariable("id") UUID id,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    mealPlanService.deleteMealPlan(userId, id);
    return ApiResponse.success(null);
  }

  @PostMapping("/{id}/items/{itemId}/swap")
  @Operation(summary = "Swap a meal plan item", description = "Swaps a food item in the meal plan with an equivalent nutrition dish.")
  public ApiResponse<MealPlanResponse> swapItem(
      @PathVariable("id") UUID id,
      @PathVariable("itemId") UUID itemId,
      @RequestBody(required = false) SwapFoodItemRequest request,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(mealPlanService.swapItem(userId, id, itemId, request));
  }

  @PostMapping("/{id}/apply")
  @Operation(summary = "Apply meal plan to diary", description = "Converts meal plan items into UserFoodLog entries for the specified date and recalculates summary.")
  public ApiResponse<ApplyMealPlanResponse> applyMealPlan(
      @PathVariable("id") UUID id,
      @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(mealPlanService.applyMealPlan(userId, id, date));
  }
}
