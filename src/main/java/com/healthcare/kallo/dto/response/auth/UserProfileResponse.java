package com.healthcare.kallo.dto.response.auth;

import java.math.BigDecimal;
import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String email,
    boolean emailVerified,
    String displayName,
    String timezone,
    String avatarUrl,
    String role,
    BigDecimal weightKg) {
  public UserProfileResponse(UUID id, String email, boolean emailVerified, String displayName,
      String timezone, String avatarUrl, String role) {
    this(id, email, emailVerified, displayName, timezone, avatarUrl, role, null);
  }
}
