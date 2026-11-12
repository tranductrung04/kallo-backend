package com.healthcare.kallo.common.event;

import java.time.Instant;
import java.util.UUID;

public record WaterLoggedEvent(UUID userId, Instant loggedAt) {}
