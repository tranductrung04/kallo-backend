package com.healthcare.kallo.dto.response.act;

import com.healthcare.kallo.model.act.enums.HealthDataType;
import com.healthcare.kallo.model.act.enums.HealthProvider;
import java.time.Instant;

public record HealthSyncStatusResponse(
    HealthProvider provider, HealthDataType dataType, Instant lastEndTime, Instant lastSyncedAt) {}
