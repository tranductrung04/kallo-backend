package com.healthcare.kallo.dto.request.profile;

import com.healthcare.kallo.model.profile.enums.ActivityLevel;
import com.healthcare.kallo.model.profile.enums.Gender;
import com.healthcare.kallo.model.profile.enums.GoalType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record HealthProfileRequest(
    @NotNull(message = "Gender is required")
    @Schema(description = "User biological sex", example = "MALE")
    Gender gender,

    @NotNull(message = "Birth date is required")
    @Past(message = "Birth date must be in the past")
    @Schema(description = "Date of birth", example = "1998-05-15")
    LocalDate birthDate,

    @NotNull(message = "Height is required")
    @DecimalMin(value = "100.0", message = "Height must be at least 100 cm")
    @DecimalMax(value = "250.0", message = "Height must be at most 250 cm")
    @Schema(description = "Height in centimeters (100.0 - 250.0)", example = "172.5")
    BigDecimal heightCm,

    @NotNull(message = "Weight is required")
    @DecimalMin(value = "20.0", message = "Weight must be at least 20 kg")
    @DecimalMax(value = "300.0", message = "Weight must be at most 300 kg")
    @Schema(description = "Current body weight in kilograms (20.0 - 300.0)", example = "68.0")
    BigDecimal weightKg,

    @NotNull(message = "Activity level is required")
    @Schema(description = "Physical activity level", example = "MODERATELY_ACTIVE")
    ActivityLevel activityLevel,

    @NotNull(message = "Goal type is required")
    @Schema(description = "Weight and health goal", example = "LOSE_WEIGHT")
    GoalType goalType,

    @DecimalMin(value = "20.0", message = "Target weight must be at least 20 kg")
    @DecimalMax(value = "300.0", message = "Target weight must be at most 300 kg")
    @Schema(description = "Target weight in kg (optional for MAINTAIN_WEIGHT)", example = "64.0")
    BigDecimal targetWeightKg,

    @DecimalMin(value = "0.0", message = "Weekly rate cannot be negative")
    @DecimalMax(value = "2.0", message = "Weekly rate cannot exceed 2.0 kg/week")
    @Schema(description = "Target weight change rate in kg/week (0.25 - 1.0 recommended)", example = "0.5")
    BigDecimal weeklyRateKg,

    @DecimalMin(value = "5.0", message = "Custom protein must be at least 5%")
    @DecimalMax(value = "80.0", message = "Custom protein cannot exceed 80%")
    @Schema(description = "Custom protein percentage (optional)", example = "30.0")
    BigDecimal customProteinPct,

    @DecimalMin(value = "5.0", message = "Custom carbs must be at least 5%")
    @DecimalMax(value = "80.0", message = "Custom carbs cannot exceed 80%")
    @Schema(description = "Custom carbs percentage (optional)", example = "40.0")
    BigDecimal customCarbsPct,

    @DecimalMin(value = "5.0", message = "Custom fat must be at least 5%")
    @DecimalMax(value = "80.0", message = "Custom fat cannot exceed 80%")
    @Schema(description = "Custom fat percentage (optional)", example = "30.0")
    BigDecimal customFatPct,

    @Min(value = 500, message = "Water goal must be at least 500 ml")
    @Max(value = 10000, message = "Water goal cannot exceed 10000 ml")
    @Schema(description = "Custom daily water intake goal in ml (optional)", example = "2400")
    Integer customWaterGoalMl,

    @Schema(description = "List of food allergens or dietary restrictions", example = "[\"PEANUT\", \"SEAFOOD\"]")
    List<String> allergies) {}
