package com.healthcare.kallo.event.profile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProfileUpdatedEvent(
    UUID userId,
    BigDecimal weightKg,
    Instant updatedAt
) {}
