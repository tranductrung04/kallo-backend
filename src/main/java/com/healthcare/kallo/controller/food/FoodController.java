package com.healthcare.kallo.controller.food;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.common.PageResponse;
import com.healthcare.kallo.dto.request.food.CustomFoodRequest;
import com.healthcare.kallo.dto.request.food.RecipeFoodRequest;
import com.healthcare.kallo.dto.response.food.FoodResponse;
import com.healthcare.kallo.dto.response.food.FoodSearchItemResponse;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.food.FoodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/foods")
@Tag(name = "Foods", description = "Food dictionary, recipes and custom food management")
public class FoodController {

  private final FoodService foodService;

  public FoodController(FoodService foodService) {
    this.foodService = foodService;
  }

  @GetMapping("/search")
  @Operation(summary = "Search food items with pagination and filters")
  public ApiResponse<PageResponse<FoodSearchItemResponse>> search(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) FoodCategory category,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Authentication authentication) {
    UUID currentUserId = CurrentUser.id(authentication);
    return ApiResponse.success(foodService.searchFoods(currentUserId, q, category, page, size));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get detailed food item by ID")
  public ApiResponse<FoodResponse> getById(
      @PathVariable UUID id,
      Authentication authentication) {
    UUID currentUserId = CurrentUser.id(authentication);
    return ApiResponse.success(foodService.getFoodById(currentUserId, id));
  }

  @PostMapping("/custom")
  @Operation(summary = "Create custom food with direct nutrient numbers")
  public ResponseEntity<ApiResponse<FoodResponse>> createCustom(
      @Valid @RequestBody CustomFoodRequest request,
      Authentication authentication) {
    UUID currentUserId = CurrentUser.id(authentication);
    FoodResponse response = foodService.createCustomFood(currentUserId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @PostMapping("/recipe")
  @Operation(summary = "Create recipe food from a list of ingredients with automatic nutrient snapshot calculation")
  public ResponseEntity<ApiResponse<FoodResponse>> createRecipe(
      @Valid @RequestBody RecipeFoodRequest request,
      Authentication authentication) {
    UUID currentUserId = CurrentUser.id(authentication);
    FoodResponse response = foodService.createRecipeFood(currentUserId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @PutMapping("/custom/{id}")
  @Operation(summary = "Update custom food item")
  public ApiResponse<FoodResponse> updateCustom(
      @PathVariable UUID id,
      @Valid @RequestBody CustomFoodRequest request,
      Authentication authentication) {
    UUID currentUserId = CurrentUser.id(authentication);
    return ApiResponse.success(foodService.updateCustomFood(currentUserId, id, request));
  }

  @DeleteMapping("/custom/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Soft delete custom food item")
  public void deleteCustom(
      @PathVariable UUID id,
      Authentication authentication) {
    UUID currentUserId = CurrentUser.id(authentication);
    foodService.deleteCustomFood(currentUserId, id);
  }
}
