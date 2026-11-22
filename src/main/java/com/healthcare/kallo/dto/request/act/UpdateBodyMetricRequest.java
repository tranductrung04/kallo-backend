package com.healthcare.kallo.dto.request.act;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public record UpdateBodyMetricRequest(
    @NotNull @DecimalMin("20.0") @DecimalMax("300.0") BigDecimal waistCm,
    @NotNull @DecimalMin("20.0") @DecimalMax("300.0") BigDecimal hipCm,
    @NotNull Instant loggedAt,
    @Size(max = 255) String note,
    Instant clientUpdatedAt) {}
