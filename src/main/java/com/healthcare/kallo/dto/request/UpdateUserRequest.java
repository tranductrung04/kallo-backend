package com.healthcare.kallo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.healthcare.kallo.validation.ValidTimezone;

public record UpdateUserRequest(
    @NotBlank @Size(max = 100) String displayName,
    @NotBlank @Size(max = 50) @ValidTimezone String timezone) {}
