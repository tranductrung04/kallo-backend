package com.healthcare.kallo.dto.request.plan;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record GenerateMealPlanRequest(
    LocalDate planDate,
    @Min(1) @Max(7) Integer days,
    @Min(500) @Max(10000) Integer calorieTargetOverride,
    @Min(0) Integer maxBudgetVnd,
    List<UUID> excludedFoodIds
) {}
