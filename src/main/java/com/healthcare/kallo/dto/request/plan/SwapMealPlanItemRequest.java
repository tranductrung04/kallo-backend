package com.healthcare.kallo.dto.request.plan;

import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;

public record SwapMealPlanItemRequest(
    @Min(1) Integer maxBudgetVndOverride, List<UUID> excludedFoodIds) {}
