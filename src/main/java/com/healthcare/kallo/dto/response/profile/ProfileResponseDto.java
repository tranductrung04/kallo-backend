package com.healthcare.kallo.dto.response.profile;

import com.healthcare.kallo.model.profile.enums.ActivityLevel;
import com.healthcare.kallo.model.profile.enums.Gender;
import com.healthcare.kallo.model.profile.enums.GoalType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ProfileResponseDto(
    UUID userId,
    Gender gender,
    LocalDate dob,
    Integer age,
    BigDecimal heightCm,
    BigDecimal weightKg,
    BigDecimal bmi,
    ActivityLevel activityLevel,
    GoalType goalType,
    BigDecimal targetWeightKg,
    BigDecimal weeklyRateKg,
    BigDecimal bmr,
    BigDecimal tdee,
    BigDecimal dailyCalorieGoal,
    MacronutrientDto macronutrients,
    BigDecimal waterLiters,
    Boolean rateClamped,
    Boolean recalculationRecommended,
    BigDecimal recommendedTdee,
    BigDecimal recommendedDailyCalorieGoal,
    BigDecimal latestLoggedWeightKg,
    String goalAdvice,
    Long version
) {}
