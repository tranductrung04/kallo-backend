package com.healthcare.kallo.dto.request.act;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record UpdateWeightLogRequest(
    @NotNull(message = "weightKg is required")
        BigDecimal weightKg,
    @NotNull(message = "loggedAt is required") Instant loggedAt,
    @Size(max = 255, message = "note must be at most 255 characters") String note,
    Instant clientUpdatedAt) {}
