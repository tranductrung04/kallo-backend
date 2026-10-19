package com.healthcare.kallo.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentUser {

  private CurrentUser() {}

  public static UUID id(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
      throw new IllegalStateException("Authenticated principal is not a JWT");
    }
    return UUID.fromString(jwt.getSubject());
  }
}
