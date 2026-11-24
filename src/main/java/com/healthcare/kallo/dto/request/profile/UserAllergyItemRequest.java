package com.healthcare.kallo.dto.request.profile;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UserAllergyItemRequest(
    @NotNull(message = "ingredientId is required")
    UUID ingredientId,
    String note
) {}
