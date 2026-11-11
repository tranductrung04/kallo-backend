package com.healthcare.kallo.dto.request.act;

import com.healthcare.kallo.model.act.enums.HealthProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record HealthSyncRequest(
    @NotNull HealthProvider provider,
    @NotEmpty List<@NotNull @Valid HealthSyncRecordRequest> records) {}
