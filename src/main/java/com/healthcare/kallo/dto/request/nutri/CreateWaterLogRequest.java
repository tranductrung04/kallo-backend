package com.healthcare.kallo.dto.request.nutri;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CreateWaterLogRequest(
    @NotNull @Min(1) @Max(5000) Integer amountMl,
    @NotNull LocalDate loggedDate,
    Instant loggedAt,
    @NotNull UUID clientUuid,
    String note
) {}
