package com.healthcare.kallo.controller.act;

import com.healthcare.kallo.common.*;
import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.request.act.*;
import com.healthcare.kallo.dto.response.act.ActivityResponse;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.act.ActivityLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/activities")
@Tag(name = "Activities", description = "Manual exercise tracking")
public class ActivityController {
  private final ActivityLogService activities;
  public ActivityController(ActivityLogService activities) { this.activities = activities; }

  @GetMapping
  @Operation(summary = "List activities")
  public ApiResponse<PageResponse<ActivityResponse>> list(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size,
      @RequestParam(defaultValue = "false") boolean includeSuperseded, Authentication authentication) {
    return ApiResponse.success(activities.list(CurrentUser.id(authentication), from, to, page, size, includeSuperseded));
  }

  @GetMapping("/{id}")
  public ApiResponse<ActivityResponse> get(@PathVariable String id, Authentication authentication) {
    UUID activityId = parseUuid(id);
    return ApiResponse.success(activities.getById(CurrentUser.id(authentication), activityId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<ActivityResponse>> create(@Valid @RequestBody CreateActivityRequest request,
      Authentication authentication) {
    var result = activities.create(CurrentUser.id(authentication), request);
    var response = result.response();
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .location(URI.create("/api/activities/" + response.id())).body(ApiResponse.success(response));
  }

  @PutMapping("/{id}")
  public ApiResponse<ActivityResponse> update(@PathVariable String id, @Valid @RequestBody UpdateActivityRequest request,
      Authentication authentication) {
    return ApiResponse.success(activities.update(CurrentUser.id(authentication), parseUuid(id), request));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String id, Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    try {
      activities.delete(userId, UUID.fromString(id));
    } catch (IllegalArgumentException exception) {
      activities.deleteByClientUuid(userId, UUID.nameUUIDFromBytes(id.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
  }

  private UUID parseUuid(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      return UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
  }
}
