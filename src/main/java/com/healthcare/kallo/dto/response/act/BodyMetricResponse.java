package com.healthcare.kallo.dto.response.act;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BodyMetricResponse(
    UUID id, UUID userId, UUID clientUuid, BigDecimal waistCm, BigDecimal hipCm,
    Instant loggedAt, LocalDate localDate, BigDecimal whr, String riskLevel, String note, Instant createdAt, Instant updatedAt,
    Instant deletedAt) {}
