package com.healthcare.kallo.common.event;

import java.time.LocalDate;
import java.util.UUID;

public record DailySummaryChangedEvent(
    UUID userId, LocalDate date, double caloriesIn, double calorieGoal) {}
