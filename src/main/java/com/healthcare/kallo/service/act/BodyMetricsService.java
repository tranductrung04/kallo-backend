package com.healthcare.kallo.service.act;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface BodyMetricsService {
  Optional<BigDecimal> weightAt(UUID userId, Instant instant);
}
