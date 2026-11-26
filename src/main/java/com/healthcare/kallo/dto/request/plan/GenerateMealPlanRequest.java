package com.healthcare.kallo.dto.request.plan;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record GenerateMealPlanRequest(
    @NotNull LocalDate planDate,
    @Min(1) @Max(7) Integer days,
    @Min(1) Integer calorieTargetOverride,
    @Min(1) Integer maxBudgetVnd,
    List<UUID> excludedFoodIds,
    Boolean includeCustomFoods,
    @Size(max = 150) String name) {}
