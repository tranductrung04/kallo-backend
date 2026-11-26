package com.healthcare.kallo.controller.plan;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.common.PageResponse;
import com.healthcare.kallo.dto.request.plan.GenerateMealPlanRequest;
import com.healthcare.kallo.dto.request.plan.SwapMealPlanItemRequest;
import com.healthcare.kallo.dto.response.plan.MealPlanResponse;
import com.healthcare.kallo.dto.response.plan.MealPlanSummaryResponse;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.plan.MealPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/meal-plans")
@Tag(name = "Meal plans", description = "Personalized menu generation, swapping and NUTRI log application")
public class MealPlanController {

  private final MealPlanService mealPlanService;

  public MealPlanController(MealPlanService mealPlanService) {
    this.mealPlanService = mealPlanService;
  }

  @PostMapping("/generate")
  @Operation(summary = "Generate a personalized 1-7 day draft meal plan")
  public ResponseEntity<ApiResponse<MealPlanResponse>> generate(
      @Valid @RequestBody GenerateMealPlanRequest request, Authentication authentication) {
    MealPlanResponse response =
        mealPlanService.generate(CurrentUser.id(authentication), request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .location(URI.create("/api/meal-plans/" + response.id()))
        .body(ApiResponse.success(response));
  }

  @PostMapping("/{id}/save")
  @Operation(summary = "Transition a DRAFT meal plan to SAVED")
  public ApiResponse<MealPlanResponse> save(
      @PathVariable UUID id, Authentication authentication) {
    return ApiResponse.success(mealPlanService.save(CurrentUser.id(authentication), id));
  }

  @GetMapping
  @Operation(summary = "List meal plans belonging to the authenticated user")
  public ApiResponse<PageResponse<MealPlanSummaryResponse>> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Authentication authentication) {
    return ApiResponse.success(mealPlanService.list(CurrentUser.id(authentication), page, size));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get full meal plan details grouped by day and meal slot")
  public ApiResponse<MealPlanResponse> getById(
      @PathVariable UUID id, Authentication authentication) {
    return ApiResponse.success(mealPlanService.getById(CurrentUser.id(authentication), id));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a meal plan")
  public void delete(@PathVariable UUID id, Authentication authentication) {
    mealPlanService.delete(CurrentUser.id(authentication), id);
  }

  @PostMapping("/{id}/items/{itemId}/swap")
  @Operation(summary = "Swap a single item in the meal plan with a nutritionally compatible dish")
  public ApiResponse<MealPlanResponse> swapItem(
      @PathVariable UUID id,
      @PathVariable UUID itemId,
      @RequestBody(required = false) @Valid SwapMealPlanItemRequest request,
      Authentication authentication) {
    return ApiResponse.success(
        mealPlanService.swapItem(CurrentUser.id(authentication), id, itemId, request));
  }

  @PostMapping("/{id}/apply")
  @Operation(summary = "Apply a SAVED meal plan into NUTRI food logs")
  public ApiResponse<MealPlanResponse> apply(
      @PathVariable UUID id,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @RequestParam(required = false) Integer dayIndex,
      Authentication authentication) {
    return ApiResponse.success(
        mealPlanService.apply(CurrentUser.id(authentication), id, date, dayIndex));
  }
}
