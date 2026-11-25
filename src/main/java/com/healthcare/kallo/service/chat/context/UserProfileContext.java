package com.healthcare.kallo.service.chat.context;

import com.healthcare.kallo.model.profile.enums.ActivityLevel;
import com.healthcare.kallo.model.profile.enums.Gender;
import java.math.BigDecimal;

public record UserProfileContext(
    Integer age,
    Gender gender,
    BigDecimal heightCm,
    BigDecimal currentWeightKg,
    BigDecimal targetWeightKg,
    ActivityLevel activityLevel) {}
