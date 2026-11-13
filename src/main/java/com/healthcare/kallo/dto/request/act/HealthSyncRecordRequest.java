package com.healthcare.kallo.dto.request.act;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.healthcare.kallo.model.act.enums.HealthDataType;
import java.math.BigDecimal;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HealthSyncRecordRequest(
    HealthDataType dataType,
    String externalId,
    Instant startTime,
    Instant endTime,
    BigDecimal value,
    @JsonAlias("min") BigDecimal valueMin,
    @JsonAlias("max") BigDecimal valueMax,
    String dataOrigin) {}
