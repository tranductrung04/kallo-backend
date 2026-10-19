package com.healthcare.kallo.dto.request;

import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;

public record ChangePasswordRequest(
    @NotBlank @Size(max = 72) String currentPassword,
    @NotBlank
        @Size(min = 8, max = 72)
        @Pattern(regexp = ".*[A-Za-z].*", message = "must contain a letter")
        @Pattern(regexp = ".*\\d.*", message = "must contain a number")
        String newPassword) {
  @AssertTrue(message = "must not exceed 72 UTF-8 bytes")
  public boolean isNewPasswordWithinBcryptLimit() {
    return newPassword == null || newPassword.getBytes(StandardCharsets.UTF_8).length <= 72;
  }

  @AssertTrue(message = "must not exceed 72 UTF-8 bytes")
  public boolean isCurrentPasswordWithinBcryptLimit() {
    return currentPassword == null || currentPassword.getBytes(StandardCharsets.UTF_8).length <= 72;
  }
}
