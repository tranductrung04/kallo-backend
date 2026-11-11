package com.healthcare.kallo.service.act;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface ProfileBodyMetrics {
  Optional<BigDecimal> weightKg(UUID userId);

  Optional<BigDecimal> heightCm(UUID userId);
}
