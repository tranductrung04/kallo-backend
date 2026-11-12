package com.healthcare.kallo.event.sys;

import java.time.LocalDate;
import java.util.UUID;

public record DailySummaryChangedEvent(
    UUID userId, LocalDate date, double caloriesIn, double calorieGoal) {}
