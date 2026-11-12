package com.healthcare.kallo.service.sys.sync;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SyncChange(
    SyncEntityType entityType,
    UUID clientUuid,
    SyncOperationType operation,
    Map<String, Object> data,
    Instant updatedAt,
    long syncSeq) {}
