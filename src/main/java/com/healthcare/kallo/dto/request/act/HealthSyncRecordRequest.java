package com.healthcare.kallo.dto.request.act;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.healthcare.kallo.model.act.enums.HealthDataType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HealthSyncRecordRequest(
    @NotNull HealthDataType dataType,
    @NotBlank @Size(max = 100) String externalId,
    @NotNull Instant startTime,
    @NotNull Instant endTime,
    @NotNull @DecimalMin("0.0") BigDecimal value,
    @JsonAlias("min") @DecimalMin("0.0") BigDecimal valueMin,
    @JsonAlias("max") @DecimalMin("0.0") BigDecimal valueMax,
    @Size(max = 100) String dataOrigin) {}
