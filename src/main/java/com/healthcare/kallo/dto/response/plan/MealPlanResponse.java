package com.healthcare.kallo.dto.response.plan;

import com.healthcare.kallo.model.plan.enums.MealPlanStatus;
import com.healthcare.kallo.model.plan.enums.MealPlanType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MealPlanResponse(
    UUID id,
    String name,
    String description,
    MealPlanType type,
    LocalDate planDate,
    int daysCount,
    MealPlanStatus status,
    int targetCalories,
    int totalCalories,
    BigDecimal totalProteinG,
    BigDecimal totalCarbsG,
    BigDecimal totalFatG,
    BigDecimal totalSugarG,
    BigDecimal totalFiberG,
    int estimatedCostVnd,
    Integer maxBudgetVnd,
    BigDecimal preferenceMatchScore,
    String medicalWarning,
    Instant appliedAt,
    LocalDate appliedDate,
    List<MealPlanDayResponse> days,
    Instant createdAt,
    Instant updatedAt) {}
