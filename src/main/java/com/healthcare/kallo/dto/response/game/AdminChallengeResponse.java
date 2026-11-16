package com.healthcare.kallo.dto.response.game;

import com.healthcare.kallo.model.game.enums.ChallengeMetric;
import java.time.LocalDate;
import java.util.UUID;

public record AdminChallengeResponse(
    UUID id,
    String title,
    String description,
    ChallengeMetric metric,
    int target,
    LocalDate startDate,
    LocalDate endDate,
    int pointsReward,
    UUID badgeId,
    boolean active) {}
