package com.healthcare.kallo.dto.response;

import java.util.UUID;

public record UserSummary(UUID id, String email, String displayName, String role, boolean emailVerified) {
  public UserSummary(UUID id, String email, String displayName, String role) {
    this(id, email, displayName, role, false);
  }
}
