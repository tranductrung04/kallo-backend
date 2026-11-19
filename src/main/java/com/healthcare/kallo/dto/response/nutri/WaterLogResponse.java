package com.healthcare.kallo.dto.response.nutri;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WaterLogResponse(
    UUID id,
    int amountMl,
    LocalDate loggedDate,
    Instant loggedAt,
    UUID clientUuid,
    String note
) {}
