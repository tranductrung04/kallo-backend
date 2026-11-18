package com.healthcare.kallo.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import com.healthcare.kallo.validation.ValidTimezone;

public record UpdateUserRequest(
    @NotBlank @Size(max = 100) String displayName,
    @NotBlank @Size(max = 50) @ValidTimezone String timezone,
    @Size(max = 20) List<@NotBlank @Size(max = 100) String> allergies) {
  public UpdateUserRequest(String displayName, String timezone) {
    this(displayName, timezone, null);
  }
}
