package com.healthcare.kallo.event.game;

import java.util.UUID;

public record ChallengeCompletedEvent(UUID userId, UUID challengeId, int pointsAwarded, UUID badgeId) {}
