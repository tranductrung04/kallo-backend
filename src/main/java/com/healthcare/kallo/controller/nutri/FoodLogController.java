package com.healthcare.kallo.controller.nutri;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.request.nutri.CopyMealRequest;
import com.healthcare.kallo.dto.request.nutri.CreateFoodLogRequest;
import com.healthcare.kallo.dto.request.nutri.FoodFeedbackRequest;
import com.healthcare.kallo.dto.request.nutri.UpdateFoodLogRequest;
import com.healthcare.kallo.dto.response.food.FoodSearchItemResponse;
import com.healthcare.kallo.dto.response.nutri.CopyMealResponse;
import com.healthcare.kallo.dto.response.nutri.DailyFoodLogsResponse;
import com.healthcare.kallo.dto.response.nutri.FoodLogResponse;
import com.healthcare.kallo.dto.response.nutri.FoodRecognitionResponse;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.nutri.FoodLogService;
import com.healthcare.kallo.service.nutri.FoodRecognitionService;
import com.healthcare.kallo.service.nutri.NutriRateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/food-logs")
@Tag(name = "Food logs", description = "Meal logging, copying, recent foods and AI photo recognition")
public class FoodLogController {

  private final FoodLogService foodLogService;
  private final FoodRecognitionService foodRecognitionService;
  private final NutriRateLimiter nutriRateLimiter;

  public FoodLogController(
      FoodLogService foodLogService,
      FoodRecognitionService foodRecognitionService,
      NutriRateLimiter nutriRateLimiter) {
    this.foodLogService = foodLogService;
    this.foodRecognitionService = foodRecognitionService;
    this.nutriRateLimiter = nutriRateLimiter;
  }

  @PostMapping
  @Operation(summary = "Log a meal entry idempotently by clientUuid")
  public ResponseEntity<ApiResponse<FoodLogResponse>> logFood(
      @Valid @RequestBody CreateFoodLogRequest request, Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    FoodLogService.CreateResult result = foodLogService.logFood(userId, request);
    FoodLogResponse response = result.response();
    HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status)
        .location(URI.create("/api/food-logs/" + response.id()))
        .body(ApiResponse.success(response));
  }

  @GetMapping
  @Operation(summary = "List food logs grouped by meal slot for a date")
  public ApiResponse<DailyFoodLogsResponse> getByDate(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      Authentication authentication) {
    return ApiResponse.success(foodLogService.getByDate(CurrentUser.id(authentication), date));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get a single food log entry")
  public ApiResponse<FoodLogResponse> getById(
      @PathVariable UUID id, Authentication authentication) {
    return ApiResponse.success(foodLogService.getById(CurrentUser.id(authentication), id));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Update serving amount or meal slot of a food log entry")
  public ApiResponse<FoodLogResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateFoodLogRequest request,
      Authentication authentication) {
    return ApiResponse.success(foodLogService.update(CurrentUser.id(authentication), id, request));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Soft delete a food log entry idempotently")
  public void delete(@PathVariable UUID id, Authentication authentication) {
    foodLogService.delete(CurrentUser.id(authentication), id);
  }

  @PostMapping("/copy")
  @Operation(summary = "Copy meals from a source date to a target date")
  public ApiResponse<CopyMealResponse> copyMeals(
      @Valid @RequestBody CopyMealRequest request, Authentication authentication) {
    return ApiResponse.success(foodLogService.copyMeals(CurrentUser.id(authentication), request));
  }

  @GetMapping("/recent")
  @Operation(summary = "List recently logged distinct foods")
  public ApiResponse<List<FoodSearchItemResponse>> getRecentFoods(
      @RequestParam(defaultValue = "10") int limit, Authentication authentication) {
    return ApiResponse.success(
        foodLogService.getRecentFoods(CurrentUser.id(authentication), limit));
  }

  @PostMapping(value = "/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Recognize Vietnamese food from a meal photo using AI")
  public ApiResponse<FoodRecognitionResponse> recognize(
      @RequestParam("image") MultipartFile image, Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    nutriRateLimiter.checkRecognize(userId);
    return ApiResponse.success(foodRecognitionService.recognize(userId, image));
  }

  @PostMapping("/{id}/feedback")
  @Operation(summary = "Submit consent feedback for an AI-recognized food log entry")
  public ApiResponse<FoodLogResponse> submitFeedback(
      @PathVariable UUID id,
      @Valid @RequestBody FoodFeedbackRequest request,
      Authentication authentication) {
    return ApiResponse.success(
        foodLogService.submitFeedback(CurrentUser.id(authentication), id, request));
  }
}
