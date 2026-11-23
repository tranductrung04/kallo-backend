package com.healthcare.kallo.dto.response.report;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PeriodSummaryReportResponse(
    LocalDate startDate,
    LocalDate endDate,
    int totalDays,
    int activeDaysCount,
    double complianceRatePct,
    int totalCaloriesConsumed,
    int avgCaloriesConsumed,
    int totalCaloriesBurned,
    long totalSteps,
    int avgSteps,
    int avgWaterMl,
    BigDecimal avgProteinG,
    BigDecimal avgCarbsG,
    BigDecimal avgFatG
) {}
