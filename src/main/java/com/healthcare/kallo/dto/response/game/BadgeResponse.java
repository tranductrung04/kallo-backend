package com.healthcare.kallo.dto.response.game;

import java.time.Instant;
import java.util.UUID;

public record BadgeResponse(
    UUID id,
    String code,
    String name,
    String description,
    String iconKey,
    int points,
    boolean earned,
    Instant earnedAt) {}
