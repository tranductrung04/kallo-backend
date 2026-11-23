package com.healthcare.kallo.dto.request.plan;

import jakarta.validation.constraints.Size;

public record SaveMealPlanRequest(
    @Size(max = 150) String name,
    @Size(max = 1000) String description
) {}
