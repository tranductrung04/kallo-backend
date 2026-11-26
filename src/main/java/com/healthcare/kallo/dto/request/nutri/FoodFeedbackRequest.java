package com.healthcare.kallo.dto.request.nutri;

import jakarta.validation.constraints.NotNull;

public record FoodFeedbackRequest(@NotNull Boolean consented) {}
