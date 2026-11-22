package com.healthcare.kallo.dto.response.auth;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String email,
    boolean emailVerified,
    String displayName,
    String timezone,
    String avatarUrl,
    String role,
    List<String> allergies,
    BigDecimal heightCm,
    String gender,
    LocalDate birthDate,
    BigDecimal targetWeightKg,
    Integer stepGoal) {
  public UserProfileResponse(UUID id, String email, boolean emailVerified, String displayName,
      String timezone, String avatarUrl, String role) {
    this(id, email, emailVerified, displayName, timezone, avatarUrl, role, List.of(), null, null, null, null, null);
  }
}
