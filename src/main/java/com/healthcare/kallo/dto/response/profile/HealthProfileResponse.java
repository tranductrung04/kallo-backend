package com.healthcare.kallo.dto.response.profile;

import com.healthcare.kallo.model.profile.enums.ActivityLevel;
import com.healthcare.kallo.model.profile.enums.BmiCategory;
import com.healthcare.kallo.model.profile.enums.Gender;
import com.healthcare.kallo.model.profile.enums.GoalType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record HealthProfileResponse(
    UUID id,
    UUID userId,
    Gender gender,
    LocalDate birthDate,
    int age,
    BigDecimal heightCm,
    BigDecimal weightKg,
    ActivityLevel activityLevel,
    GoalType goalType,
    BigDecimal targetWeightKg,
    BigDecimal weeklyRateKg,
    BigDecimal bmi,
    BmiCategory bmiCategory,
    String bmiCategoryVi,
    int bmr,
    int tdee,
    int dailyCalorieGoal,
    @Schema(description = "True if daily calorie goal was adjusted up to safety floor")
    boolean safetyClamped,
    BigDecimal proteinGoalG,
    BigDecimal carbsGoalG,
    BigDecimal fatGoalG,
    BigDecimal proteinPct,
    BigDecimal carbsPct,
    BigDecimal fatPct,
    boolean customMacro,
    int waterGoalMl,
    List<String> allergies,
    @Schema(description = "True if latest weight log suggests recalculating TDEE/calorie goal")
    boolean recalculationRecommended,
    Integer recommendedTdee,
    Integer recommendedDailyCalorieGoal,
    BigDecimal latestLoggedWeightKg,
    Instant createdAt,
    Instant updatedAt) {}
