package com.healthcare.kallo.dto.request.profile;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record UserChronicConditionItemRequest(
    @NotNull(message = "conditionId is required")
    UUID conditionId,
    LocalDate diagnosedAt,
    String note
) {}
