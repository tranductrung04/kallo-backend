package com.healthcare.kallo.dto.request.profile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateUserChronicConditionsRequest(
    @NotNull(message = "conditions list cannot be null")
    @Valid
    List<UserChronicConditionItemRequest> conditions
) {}
