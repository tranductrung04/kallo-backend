package com.healthcare.kallo.dto.request.game;

import jakarta.validation.constraints.NotNull;

public record UpdateGameSettingsRequest(
    @NotNull Boolean discoverable,
    @NotNull Boolean shareStreak,
    @NotNull Boolean sharePoints,
    @NotNull Boolean shareSteps) {}
