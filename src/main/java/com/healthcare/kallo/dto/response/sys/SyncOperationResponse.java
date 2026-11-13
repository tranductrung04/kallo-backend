package com.healthcare.kallo.dto.response.sys;

import com.healthcare.kallo.service.sys.sync.SyncApplyResult;
import com.healthcare.kallo.service.sys.sync.SyncApplyStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SyncOperationResponse(
    String entityType,
    UUID clientUuid,
    SyncApplyStatus status,
    String reason,
    Map<String, Object> serverData,
    Instant serverUpdatedAt) {
  public static SyncOperationResponse from(
      String entityType, UUID clientUuid, SyncApplyResult result) {
    return new SyncOperationResponse(
        entityType,
        clientUuid,
        result.status(),
        result.reason(),
        result.serverData(),
        result.serverUpdatedAt());
  }
}
