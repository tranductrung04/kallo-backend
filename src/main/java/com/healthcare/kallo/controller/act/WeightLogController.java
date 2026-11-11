package com.healthcare.kallo.controller.act;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.request.act.CreateWeightLogRequest;
import com.healthcare.kallo.dto.request.act.UpdateWeightLogRequest;
import com.healthcare.kallo.dto.response.act.WeightLogResponse;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.act.WeightLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/weight-logs")
@Tag(name = "Weight logs", description = "Body weight tracking")
public class WeightLogController {
  private final WeightLogService weightLogs;

  public WeightLogController(WeightLogService weightLogs) {
    this.weightLogs = weightLogs;
  }

  @GetMapping
  @Operation(summary = "List weight logs by date range")
  public ApiResponse<List<WeightLogResponse>> list(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      Authentication authentication) {
    return ApiResponse.success(weightLogs.list(CurrentUser.id(authentication), from, to));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get a single weight log")
  public ApiResponse<WeightLogResponse> get(
      @PathVariable UUID id, Authentication authentication) {
    return ApiResponse.success(
        weightLogs
            .getById(CurrentUser.id(authentication), id)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)));
  }

  @PostMapping
  @Operation(summary = "Create a weight log")
  public ResponseEntity<ApiResponse<WeightLogResponse>> create(
      @Valid @RequestBody CreateWeightLogRequest request, Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    WeightLogService.CreateResult result = weightLogs.create(userId, request);
    WeightLogResponse response = result.response();
    HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status)
        .location(URI.create("/api/weight-logs/" + response.id()))
        .body(ApiResponse.success(response));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Update a weight log")
  public ApiResponse<WeightLogResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateWeightLogRequest request,
      Authentication authentication) {
    return ApiResponse.success(weightLogs.update(CurrentUser.id(authentication), id, request));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete a weight log")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id, Authentication authentication) {
    weightLogs.delete(CurrentUser.id(authentication), id);
  }
}
