package com.healthcare.kallo.dto.request.act;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateBodyMetricRequest(
    @NotNull UUID clientUuid,
    @NotNull @DecimalMin("20.0") @DecimalMax("300.0") BigDecimal waistCm,
    @NotNull @DecimalMin("20.0") @DecimalMax("300.0") BigDecimal hipCm,
    Instant loggedAt,
    @Size(max = 255) String note) {
  public Instant loggedAtOrNow() { return loggedAt == null ? Instant.now() : loggedAt; }
}
