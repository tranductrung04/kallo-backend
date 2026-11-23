package com.healthcare.kallo.dto.response.plan;

import com.healthcare.kallo.dto.response.report.DailySummaryResponse;
import java.time.LocalDate;
import java.util.UUID;

public record ApplyMealPlanResponse(
    UUID mealPlanId,
    LocalDate appliedDate,
    int logsCreatedCount,
    int totalCaloriesLogged,
    DailySummaryResponse dailySummary
) {}
