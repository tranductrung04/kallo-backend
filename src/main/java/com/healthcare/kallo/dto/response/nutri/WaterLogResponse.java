package com.healthcare.kallo.dto.response.nutri;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WaterLogResponse(
    UUID id,
    Integer amountMl,
    LocalDate loggedDate,
    Instant loggedAt,
    String note,
    UUID clientUuid,
    Instant createdAt,
    Instant updatedAt) {}
