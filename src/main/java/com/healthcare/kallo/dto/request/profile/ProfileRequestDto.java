package com.healthcare.kallo.dto.request.profile;

import com.healthcare.kallo.model.profile.enums.ActivityLevel;
import com.healthcare.kallo.model.profile.enums.Gender;
import com.healthcare.kallo.model.profile.enums.GoalType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ProfileRequestDto(
    @NotNull(message = "gender is required")
    Gender gender,

    @NotNull(message = "dob is required")
    @Past(message = "dob must be in the past")
    LocalDate dob,

    @NotNull(message = "heightCm is required")
    @DecimalMin(value = "50.0", message = "heightCm must be at least 50.0")
    @DecimalMax(value = "250.0", message = "heightCm must be at most 250.0")
    BigDecimal heightCm,

    @NotNull(message = "weightKg is required")
    @DecimalMin(value = "20.0", message = "weightKg must be at least 20.0")
    @DecimalMax(value = "300.0", message = "weightKg must be at most 300.0")
    BigDecimal weightKg,

    @NotNull(message = "activityLevel is required")
    ActivityLevel activityLevel,

    @NotNull(message = "goalType is required")
    GoalType goalType,

    @DecimalMin(value = "20.0", message = "targetWeightKg must be at least 20.0")
    @DecimalMax(value = "300.0", message = "targetWeightKg must be at most 300.0")
    BigDecimal targetWeightKg,

    @DecimalMin(value = "0.00", message = "weeklyRateKg must be at least 0.00")
    @DecimalMax(value = "1.50", message = "weeklyRateKg must be at most 1.50")
    BigDecimal weeklyRateKg,

    @Min(value = 1000, message = "stepGoal must be at least 1000")
    @Max(value = 100000, message = "stepGoal must be at most 100000")
    Integer stepGoal
) {
  public ProfileRequestDto(
      Gender gender,
      LocalDate dob,
      BigDecimal heightCm,
      BigDecimal weightKg,
      ActivityLevel activityLevel,
      GoalType goalType,
      BigDecimal targetWeightKg,
      BigDecimal weeklyRateKg) {
    this(gender, dob, heightCm, weightKg, activityLevel, goalType, targetWeightKg, weeklyRateKg, null);
  }
}
