package com.healthcare.kallo.dto.response.auth;

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
    List<String> allergies) {
  public UserProfileResponse(UUID id, String email, boolean emailVerified, String displayName,
      String timezone, String avatarUrl, String role) {
    this(id, email, emailVerified, displayName, timezone, avatarUrl, role, List.of());
  }
}
