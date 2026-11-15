package com.healthcare.kallo.service.game;

import java.util.UUID;

public record BadgeRuleContext(
    UUID userId,
    int longestStreak,
    boolean firstLog,
    long friendsCount,
    long challengesCompleted,
    boolean weightGoalReached) {}
