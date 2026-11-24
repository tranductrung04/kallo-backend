package com.healthcare.kallo.dto.response.profile;

import java.util.UUID;

public record UserAllergyResponseDto(
    UUID ingredientId,
    String name,
    String note,
    boolean active
) {}
