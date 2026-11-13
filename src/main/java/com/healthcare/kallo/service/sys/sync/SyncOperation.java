package com.healthcare.kallo.service.sys.sync;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SyncOperation(
    UUID clientUuid,
    SyncEntityType entityType,
    SyncOperationType operation,
    Instant updatedAt,
    Map<String, Object> data,
    String rawEntityType) {
  public SyncOperation(
      UUID clientUuid,
      SyncEntityType entityType,
      SyncOperationType operation,
      Instant updatedAt,
      Map<String, Object> data) {
    this(clientUuid, entityType, operation, updatedAt, data, entityType == null ? null : entityType.name());
  }
}
