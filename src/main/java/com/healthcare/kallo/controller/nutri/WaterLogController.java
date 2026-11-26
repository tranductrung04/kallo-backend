package com.healthcare.kallo.controller.nutri;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.request.nutri.CreateWaterLogRequest;
import com.healthcare.kallo.dto.response.nutri.DailyWaterLogsResponse;
import com.healthcare.kallo.dto.response.nutri.WaterLogResponse;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.nutri.WaterLogService;
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
@RequestMapping("/api/water-logs")
@Tag(name = "Water logs", description = "Daily water intake logging")
public class WaterLogController {

  private final WaterLogService waterLogService;

  public WaterLogController(WaterLogService waterLogService) {
    this.waterLogService = waterLogService;
  }

  @PostMapping
  @Operation(summary = "Log water intake idempotently by clientUuid")
  public ResponseEntity<ApiResponse<WaterLogResponse>> logWater(
      @Valid @RequestBody CreateWaterLogRequest request, Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    WaterLogService.CreateResult result = waterLogService.logWater(userId, request);
    WaterLogResponse response = result.response();
    HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status)
        .location(URI.create("/api/water-logs/" + response.id()))
        .body(ApiResponse.success(response));
  }

  @GetMapping
  @Operation(summary = "List water logs and daily total for a date")
  public ApiResponse<DailyWaterLogsResponse> getByDate(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      Authentication authentication) {
    return ApiResponse.success(waterLogService.getByDate(CurrentUser.id(authentication), date));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Soft delete a water log entry idempotently")
  public void delete(@PathVariable UUID id, Authentication authentication) {
    waterLogService.delete(CurrentUser.id(authentication), id);
  }
}
