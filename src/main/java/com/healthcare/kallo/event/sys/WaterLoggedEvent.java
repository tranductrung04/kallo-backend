package com.healthcare.kallo.event.sys;

import java.time.Instant;
import java.util.UUID;

public record WaterLoggedEvent(UUID userId, Instant loggedAt) {}
