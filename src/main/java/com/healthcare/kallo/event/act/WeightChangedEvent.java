package com.healthcare.kallo.event.act;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WeightChangedEvent(
    UUID userId,
    LocalDate localDate,
    BigDecimal weightKg,
    Instant loggedAt
) {
  public WeightChangedEvent(UUID userId, LocalDate localDate) {
    this(userId, localDate, null, null);
  }

  public WeightChangedEvent(UUID userId, BigDecimal weightKg, Instant loggedAt) {
    this(userId, null, weightKg, loggedAt);
  }
}
