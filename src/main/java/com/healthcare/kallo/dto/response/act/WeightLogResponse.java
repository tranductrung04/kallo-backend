package com.healthcare.kallo.dto.response.act;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WeightLogResponse(
    UUID id,
    UUID userId,
    UUID clientUuid,
    BigDecimal weightKg,
    Instant loggedAt,
    LocalDate localDate,
    String note,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt) {}
