package com.healthcare.kallo.service.profile.port;

import java.util.UUID;

public record IngredientSummary(
    UUID id,
    String code,
    String name,
    boolean active
) {
  public IngredientSummary(UUID id, String name, boolean active) {
    this(id, null, name, active);
  }
}
