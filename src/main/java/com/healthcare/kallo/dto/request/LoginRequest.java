package com.healthcare.kallo.dto.request;

import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;

public record LoginRequest(
    @NotBlank @Size(max = 255) @Email(message = "must be a valid email") String email,
    @NotBlank @Size(max = 72) String password) {
  @AssertTrue(message = "must not exceed 72 UTF-8 bytes")
  public boolean isPasswordWithinBcryptLimit() {
    return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
  }

  @Override
  public String toString() {
    return "LoginRequest[email=" + email + ", password=[REDACTED]]";
  }
}
