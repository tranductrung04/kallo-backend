package com.healthcare.kallo.dto.request.auth;
import jakarta.validation.constraints.*;
public record ForgotPasswordRequest(@NotBlank @Email String email) {}
