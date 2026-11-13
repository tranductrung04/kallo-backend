package com.healthcare.kallo.dto.request.sys;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SyncOperationRequest(
    UUID clientUuid,
    String entityType,
    String operation,
    Instant updatedAt,
    Map<String, Object> data) {}
