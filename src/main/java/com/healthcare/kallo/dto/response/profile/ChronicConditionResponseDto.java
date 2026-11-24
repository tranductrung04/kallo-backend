package com.healthcare.kallo.dto.response.profile;

import java.util.UUID;

public record ChronicConditionResponseDto(
    UUID id,
    String code,
    String name,
    String description,
    boolean isActive
) {}
