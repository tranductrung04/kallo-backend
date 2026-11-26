package com.healthcare.kallo.dto.request.nutri;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CreateWaterLogRequest(
    @NotNull @Min(1) @Max(5000) Integer amountMl,
    LocalDate loggedDate,
    Instant loggedAt,
    @NotNull UUID clientUuid,
    @Size(max = 255) String note) {}
