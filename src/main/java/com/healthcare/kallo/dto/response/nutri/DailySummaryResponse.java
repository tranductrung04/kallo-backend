package com.healthcare.kallo.dto.response.nutri;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailySummaryResponse(
    LocalDate date,
    int caloriesConsumed,
    Integer targetCalories,
    Integer remainingCalories,
    BigDecimal proteinG,
    BigDecimal carbsG,
    BigDecimal fatG,
    BigDecimal sugarG,
    BigDecimal fiberG,
    int waterMl,
    Integer targetWaterMl,
    int caloriesBurned,
    int steps,
    BigDecimal weightKg,
    boolean goalAchieved,
    boolean sugarExceeded,
    boolean proteinExceeded,
    int mealsLoggedCount) {}
