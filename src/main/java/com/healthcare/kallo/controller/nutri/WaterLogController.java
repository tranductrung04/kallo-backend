package com.healthcare.kallo.controller.nutri;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.request.nutri.CreateWaterLogRequest;
import com.healthcare.kallo.dto.response.nutri.DailyWaterResponse;
import com.healthcare.kallo.dto.response.nutri.WaterLogResponse;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.nutri.WaterLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/water-logs")
@Tag(name = "Water Logs", description = "User water intake tracking")
public class WaterLogController {

  private final WaterLogService waterLogService;

  public WaterLogController(WaterLogService waterLogService) {
    this.waterLogService = waterLogService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Log water intake", description = "Records a water intake amount in ml with client UUID for idempotency.")
  public ApiResponse<WaterLogResponse> createWaterLog(
      @Valid @RequestBody CreateWaterLogRequest request,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(waterLogService.logWater(userId, request));
  }

  @GetMapping
  @Operation(summary = "Get daily water intake", description = "Retrieves total water consumed on a date and breakdown of water logs.")
  public ApiResponse<DailyWaterResponse> getDailyWater(
      @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(waterLogService.getDailyWater(userId, date));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete water log", description = "Removes a water log entry and recalculates daily summary.")
  public ApiResponse<Void> deleteWaterLog(
      @PathVariable("id") UUID id,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    waterLogService.deleteWaterLog(userId, id);
    return ApiResponse.success(null);
  }
}
