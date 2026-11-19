package com.healthcare.kallo.dto.response.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DailySummaryResponse(
    UUID id,
    LocalDate summaryDate,
    int caloriesConsumed,
    int targetCalories,
    int caloriesRemaining,
    BigDecimal proteinG,
    BigDecimal carbsG,
    BigDecimal fatG,
    BigDecimal sugarG,
    BigDecimal fiberG,
    int waterMl,
    int targetWaterMl,
    int caloriesBurned,
    int steps,
    BigDecimal weightKg,
    boolean goalAchieved,
    boolean sugarExceeded,
    boolean proteinExceeded,
    int mealsLoggedCount
) {}
