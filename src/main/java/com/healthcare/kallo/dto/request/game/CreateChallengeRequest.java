package com.healthcare.kallo.dto.request.game;

import com.healthcare.kallo.model.game.enums.ChallengeMetric;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CreateChallengeRequest(
    @NotBlank @Size(max = 150) String title,
    @NotBlank @Size(max = 1000) String description,
    @NotNull ChallengeMetric metric,
    @NotNull @Positive Integer targetValue,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    @NotNull @Min(0) Integer pointsReward,
    UUID badgeId,
    @NotNull Boolean active) {}
