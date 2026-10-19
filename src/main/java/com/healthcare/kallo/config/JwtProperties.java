package com.healthcare.kallo.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(
    @NotBlank String secret, long accessExpiration, long refreshExpiration, long refreshReuseGrace) {
  public JwtProperties {
    if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
      throw new IllegalArgumentException("JWT_SECRET is missing or must contain at least 32 random bytes");
    }
    if (accessExpiration <= 0 || refreshExpiration <= 0)
      throw new IllegalArgumentException("JWT expirations must be positive");
    if (refreshReuseGrace < 0) {
      throw new IllegalArgumentException("JWT refresh reuse grace must be zero or positive");
    }
  }
}
