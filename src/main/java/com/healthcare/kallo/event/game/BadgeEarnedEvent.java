package com.healthcare.kallo.event.game;

import java.util.UUID;

public record BadgeEarnedEvent(UUID userId, UUID badgeId, int points) {}
