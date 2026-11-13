package com.healthcare.kallo.dto.response.game;

import java.time.LocalDate;

public record StreakResponse(
    int currentStreak,
    int longestStreak,
    LocalDate lastCompletedDate,
    boolean todayCompleted,
    boolean atRisk) {}
