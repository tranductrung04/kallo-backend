package com.healthcare.kallo.dto.response.plan;

import com.healthcare.kallo.model.plan.enums.MealPlanStatus;
import com.healthcare.kallo.model.plan.enums.MealPlanType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MealPlanSummaryResponse(
    UUID id,
    String name,
    MealPlanType type,
    LocalDate planDate,
    int daysCount,
    MealPlanStatus status,
    int targetCalories,
    int totalCalories,
    int estimatedCostVnd,
    Integer maxBudgetVnd,
    BigDecimal preferenceMatchScore,
    String medicalWarning,
    Instant createdAt) {}
