package com.healthcare.kallo.dto.response.act;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WeightLogResponse(
    UUID id,
    UUID userId,
    UUID clientUuid,
    BigDecimal weightKg,
    Instant loggedAt,
    String note,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt) {}
