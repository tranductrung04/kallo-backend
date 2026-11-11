package com.healthcare.kallo.dto.response.act;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyActivityStats(
    LocalDate date,
    long totalSteps,
    BigDecimal activityCalories,
    BigDecimal stepCalories,
    BigDecimal totalCaloriesBurned,
    int activeMinutes,
    BigDecimal averageHeartRate,
    BigDecimal maxHeartRate,
    BigDecimal weightKg,
    String stepsSource) {}
