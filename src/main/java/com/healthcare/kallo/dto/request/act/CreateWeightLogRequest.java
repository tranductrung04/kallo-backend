package com.healthcare.kallo.dto.request.act;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateWeightLogRequest(
    @NotNull(message = "clientUuid is required") UUID clientUuid,
    @NotNull(message = "weightKg is required") BigDecimal weightKg,
    Instant loggedAt,
    @Size(max = 255, message = "note must be at most 255 characters") String note) {

  public Instant loggedAtOrNow() {
    return loggedAt == null ? Instant.now() : loggedAt;
  }
}
