package com.healthcare.kallo.dto.request.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProfileUpdateRequest(
    @DecimalMin("20.0") @DecimalMax("300.0") BigDecimal weightKg,
    String activityLevel,
    String goalType) {}
