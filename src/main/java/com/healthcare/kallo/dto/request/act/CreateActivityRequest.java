package com.healthcare.kallo.dto.request.act;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.healthcare.kallo.model.act.enums.Intensity;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateActivityRequest(
    @NotNull @JsonAlias("id") String clientUuid,
    @NotBlank @Size(max = 40) @JsonAlias("type") String activityType,
    Intensity intensity,
    @NotNull @JsonAlias("timestamp") Instant startedAt,
    @Min(1) @Max(1440) @JsonAlias("durationMinutes") int durationMin,
    @DecimalMin("0.0") @DecimalMax("9999.99") BigDecimal distanceKm,
    @Size(max = 255) String note,
    String source,
    String externalId,
    String routePolyline) {
  public CreateActivityRequest {
    if (intensity == null) intensity = Intensity.MODERATE;
  }

  public CreateActivityRequest(UUID clientUuid, String activityType, Intensity intensity,
      Instant startedAt, int durationMin, BigDecimal distanceKm, String note) {
    this(clientUuid.toString(), activityType, intensity, startedAt, durationMin, distanceKm, note, null, null, null);
  }
}
