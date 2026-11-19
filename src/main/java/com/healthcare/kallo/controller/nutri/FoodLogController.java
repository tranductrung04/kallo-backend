package com.healthcare.kallo.controller.nutri;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.request.nutri.CopyMealRequest;
import com.healthcare.kallo.dto.request.nutri.CreateFoodLogRequest;
import com.healthcare.kallo.dto.request.nutri.UpdateFoodLogRequest;
import com.healthcare.kallo.dto.response.food.FoodSearchItemResponse;
import com.healthcare.kallo.dto.response.nutri.DailyFoodLogResponse;
import com.healthcare.kallo.dto.response.nutri.FoodLogResponse;
import com.healthcare.kallo.dto.response.nutri.RecognizeFoodResponse;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.nutri.FoodLogService;
import com.healthcare.kallo.service.nutri.FoodRecognitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/food-logs")
@Tag(name = "Food Logs", description = "User daily food diary and AI dish recognition")
public class FoodLogController {

  private final FoodLogService foodLogService;
  private final FoodRecognitionService foodRecognitionService;

  public FoodLogController(
      FoodLogService foodLogService,
      FoodRecognitionService foodRecognitionService) {
    this.foodLogService = foodLogService;
    this.foodRecognitionService = foodRecognitionService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Log a food item", description = "Creates a food entry for a meal slot with nutritional snapshot.")
  public ApiResponse<FoodLogResponse> createFoodLog(
      @Valid @RequestBody CreateFoodLogRequest request,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(foodLogService.logFood(userId, request));
  }

  @GetMapping
  @Operation(summary = "Get daily food logs", description = "Retrieves all meals logged for a specific date grouped by meal slot.")
  public ApiResponse<DailyFoodLogResponse> getDailyFoodLogs(
      @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(foodLogService.getDailyFoodLogs(userId, date));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get food log by ID", description = "Retrieves details of a specific food log entry.")
  public ApiResponse<FoodLogResponse> getFoodLog(
      @PathVariable("id") UUID id,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(foodLogService.getFoodLog(userId, id));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Update food log", description = "Updates meal type or serving quantity, recalculating nutritional snapshot.")
  public ApiResponse<FoodLogResponse> updateFoodLog(
      @PathVariable("id") UUID id,
      @Valid @RequestBody UpdateFoodLogRequest request,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(foodLogService.updateFoodLog(userId, id, request));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete food log", description = "Removes a food log entry and recalculates daily summary.")
  public ApiResponse<Void> deleteFoodLog(
      @PathVariable("id") UUID id,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    foodLogService.deleteFoodLog(userId, id);
    return ApiResponse.success(null);
  }

  @PostMapping("/copy")
  @Operation(summary = "Copy meals", description = "Copies meals from a previous date to target date.")
  public ApiResponse<Void> copyMeals(
      @Valid @RequestBody CopyMealRequest request,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    foodLogService.copyMeals(userId, request);
    return ApiResponse.success(null);
  }

  @GetMapping("/recent")
  @Operation(summary = "Get recently logged foods", description = "Retrieves distinct foods recently logged by user.")
  public ApiResponse<List<FoodSearchItemResponse>> getRecentFoods(
      @RequestParam(name = "limit", defaultValue = "10") int limit,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(foodLogService.getRecentFoods(userId, limit));
  }

  @PostMapping(value = "/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Recognize food from image", description = "Uploads dish image to MinIO and detects food items using AI YOLO model.")
  public ApiResponse<RecognizeFoodResponse> recognizeFood(
      @RequestParam("file") MultipartFile file,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(foodRecognitionService.recognizeFood(userId, file));
  }
}
