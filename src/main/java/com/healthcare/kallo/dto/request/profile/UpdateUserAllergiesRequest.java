package com.healthcare.kallo.dto.request.profile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateUserAllergiesRequest(
    @NotNull(message = "allergies list cannot be null")
    @Valid
    List<UserAllergyItemRequest> allergies
) {}
