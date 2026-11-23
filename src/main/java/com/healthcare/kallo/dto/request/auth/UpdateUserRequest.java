package com.healthcare.kallo.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.healthcare.kallo.validation.ValidTimezone;

public record UpdateUserRequest(
    @NotBlank @Size(max = 100) String displayName,
    @NotBlank @Size(max = 50) @ValidTimezone String timezone,
    @Size(max = 20) List<@NotBlank @Size(max = 100) String> allergies,
    @jakarta.validation.constraints.DecimalMin("50.0")
    @jakarta.validation.constraints.DecimalMax("250.0") BigDecimal heightCm,
    @Size(max = 20) String gender,
    LocalDate birthDate,
    @jakarta.validation.constraints.DecimalMin("20.0")
    @jakarta.validation.constraints.DecimalMax("300.0") BigDecimal targetWeightKg,
    @jakarta.validation.constraints.Min(1000) @jakarta.validation.constraints.Max(100000) Integer stepGoal) {
  public UpdateUserRequest(String displayName, String timezone) {
    this(displayName, timezone, null, null, null, null, null, null);
  }

  public UpdateUserRequest(String displayName, String timezone, List<String> allergies) {
    this(displayName, timezone, allergies, null, null, null, null, null);
  }
}
