package com.healthcare.kallo.dto.response.profile;

import java.time.LocalDate;
import java.util.UUID;

public record UserChronicConditionResponseDto(
    UUID conditionId,
    String code,
    String name,
    String description,
    LocalDate diagnosedAt,
    String note
) {}
