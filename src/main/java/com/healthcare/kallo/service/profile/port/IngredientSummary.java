package com.healthcare.kallo.service.profile.port;

import java.util.UUID;

public record IngredientSummary(
    UUID id,
    String name,
    boolean active
) {}
