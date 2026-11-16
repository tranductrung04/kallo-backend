package com.healthcare.kallo.dto.response.game;

import com.healthcare.kallo.model.game.enums.ChallengeMetric;
import com.healthcare.kallo.model.game.enums.ChallengeStatus;
import java.time.LocalDate;
import java.util.UUID;

public record ChallengeResponse(
    UUID id,
    String title,
    String description,
    ChallengeMetric metric,
    int target,
    LocalDate startDate,
    LocalDate endDate,
    int pointsReward,
    UUID badgeId,
    boolean joined,
    int progress,
    ChallengeStatus status,
    long daysLeft) {}
