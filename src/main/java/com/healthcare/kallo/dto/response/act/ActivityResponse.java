package com.healthcare.kallo.dto.response.act;

import com.healthcare.kallo.model.act.enums.ActivitySource;
import com.healthcare.kallo.model.act.enums.Intensity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ActivityResponse(
    UUID id, UUID userId, UUID clientUuid, String activityType, Intensity intensity,
    BigDecimal met, BigDecimal weightKgUsed, int durationMin, BigDecimal distanceKm,
    BigDecimal caloriesBurned, ActivitySource source, Instant startedAt, Instant endedAt,
    UUID supersededBy, String note, Instant createdAt, Instant updatedAt, Instant deletedAt) {}
