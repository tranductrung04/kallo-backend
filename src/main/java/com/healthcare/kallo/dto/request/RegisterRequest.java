package com.healthcare.kallo.dto.request;

import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;

public record RegisterRequest(
    @NotBlank @Size(max = 255) @Email(message = "must be a valid email") String email,
    @NotBlank
        @Size(min = 8, max = 72)
        @Pattern(regexp = ".*[A-Za-z].*", message = "must contain a letter")
        @Pattern(regexp = ".*\\d.*", message = "must contain a number")
        String password,
    @NotBlank @Size(min = 1, max = 100) String displayName) {
  @AssertTrue(message = "must not exceed 72 UTF-8 bytes")
  public boolean isPasswordWithinBcryptLimit() {
    return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
  }

  @Override
  public String toString() {
    return "RegisterRequest[email="
        + email
        + ", password=[REDACTED], displayName="
        + displayName
        + "]";
  }
}
