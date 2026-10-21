package com.healthcare.kallo.dto.request;
import jakarta.validation.constraints.*;
public record VerifyEmailRequest(@NotBlank @Pattern(regexp="\\d{6}") String code) {}
