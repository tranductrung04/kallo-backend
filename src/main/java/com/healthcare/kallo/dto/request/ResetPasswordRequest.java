package com.healthcare.kallo.dto.request;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
public record ResetPasswordRequest(@NotBlank @Email String email, @NotBlank @Pattern(regexp="\\d{6}") String code,
    @NotBlank @Size(min=8,max=72) @Pattern(regexp=".*[A-Za-z].*") @Pattern(regexp=".*\\d.*") String newPassword) {
  @AssertTrue public boolean isNewPasswordWithinBcryptLimit() {
    return newPassword == null || newPassword.getBytes(StandardCharsets.UTF_8).length <= 72;
  }
}
