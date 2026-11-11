package com.healthcare.kallo.dto.response.act;

import com.healthcare.kallo.model.act.enums.ActivitySource;
import com.healthcare.kallo.model.act.enums.Intensity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;

public record ActivityResponse(
    UUID id, UUID userId, UUID clientUuid, String activityType, Intensity intensity,
    BigDecimal met, BigDecimal weightKgUsed, int durationMin, BigDecimal distanceKm,
    BigDecimal caloriesBurned, ActivitySource source, Instant startedAt, Instant endedAt,
    UUID supersededBy, String note, Instant createdAt, Instant updatedAt, Instant deletedAt,
    @JsonInclude(JsonInclude.Include.NON_NULL) String routePolyline) {
  public ActivityResponse withoutRoute() {
    return new ActivityResponse(id, userId, clientUuid, activityType, intensity, met, weightKgUsed,
        durationMin, distanceKm, caloriesBurned, source, startedAt, endedAt, supersededBy,
        note, createdAt, updatedAt, deletedAt, null);
  }
}
