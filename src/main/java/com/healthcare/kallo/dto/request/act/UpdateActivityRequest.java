package com.healthcare.kallo.dto.request.act;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.healthcare.kallo.model.act.enums.Intensity;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateActivityRequest(
    @NotBlank @Size(max = 40) @JsonAlias("type") String activityType,
    Intensity intensity,
    @NotNull @JsonAlias("timestamp") Instant startedAt,
    @Min(1) @Max(1440) @JsonAlias("durationMinutes") int durationMin,
    @DecimalMin("0.0") @DecimalMax("9999.99") BigDecimal distanceKm,
    @Size(max = 255) String note,
    Instant clientUpdatedAt) {
  public UpdateActivityRequest {
    if (intensity == null) intensity = Intensity.MODERATE;
  }
}
