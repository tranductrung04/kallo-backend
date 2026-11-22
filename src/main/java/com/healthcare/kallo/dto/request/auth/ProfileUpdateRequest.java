package com.healthcare.kallo.dto.request.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProfileUpdateRequest(
    @DecimalMin("50.0") @DecimalMax("250.0") BigDecimal heightCm,
    String gender,
    LocalDate birthDate,
    @DecimalMin("20.0") @DecimalMax("300.0") BigDecimal targetWeightKg,
    @Min(1000) @Max(100000) Integer stepGoal,
    @DecimalMin("20.0") @DecimalMax("300.0") BigDecimal weightKg,
    String activityLevel,
    String goalType) {}
