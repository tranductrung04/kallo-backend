package com.healthcare.kallo.controller.sys;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.request.sys.SyncOperationRequest;
import com.healthcare.kallo.dto.request.sys.SyncPushRequest;
import com.healthcare.kallo.dto.response.sys.SyncPullResponse;
import com.healthcare.kallo.dto.response.sys.SyncPushResponse;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.sys.SyncService;
import com.healthcare.kallo.service.sys.sync.SyncOperation;
import com.healthcare.kallo.service.sys.sync.SyncOperationType;
import com.healthcare.kallo.service.sys.sync.SyncEntityType;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync")
public class SyncController {
  private final SyncService sync;

  public SyncController(SyncService sync) {
    this.sync = sync;
  }

  @PostMapping("/push")
  public ApiResponse<SyncPushResponse> push(
      Authentication authentication, @Valid @RequestBody SyncPushRequest request) {
    UUID userId = CurrentUser.id(authentication);
    List<SyncOperation> operations = request.operations().stream().map(this::toOperation).toList();
    return ApiResponse.success(sync.push(userId, operations));
  }

  @GetMapping("/pull")
  public ApiResponse<SyncPullResponse> pull(
      Authentication authentication,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return ApiResponse.success(sync.pull(CurrentUser.id(authentication), cursor, limit));
  }

  private SyncOperation toOperation(SyncOperationRequest request) {
    return new SyncOperation(
        request.clientUuid(),
        parseEntityType(request.entityType()),
        parseOperationType(request.operation()),
        request.updatedAt(),
        request.data(),
        request.entityType());
  }

  private SyncEntityType parseEntityType(String value) {
    if (value == null) return null;
    try {
      return SyncEntityType.valueOf(value);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private SyncOperationType parseOperationType(String value) {
    if (value == null) return null;
    try {
      return SyncOperationType.valueOf(value);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
