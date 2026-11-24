package com.healthcare.kallo.service.profile.port;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface IngredientLookup {
  Map<UUID, IngredientSummary> findSummariesByIds(Set<UUID> ids);

  List<IngredientSummary> findCommonAllergens();
}
