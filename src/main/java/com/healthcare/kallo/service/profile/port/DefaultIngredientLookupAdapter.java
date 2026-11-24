package com.healthcare.kallo.service.profile.port;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(type = "com.healthcare.kallo.service.fooddata.FoodDataIngredientLookupAdapter")
public class DefaultIngredientLookupAdapter implements IngredientLookup {

  private final Map<UUID, IngredientSummary> registry = new ConcurrentHashMap<>();

  public static final UUID PEANUT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  public static final UUID MILK_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  public static final UUID EGG_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
  public static final UUID TREE_NUT_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
  public static final UUID SOY_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
  public static final UUID WHEAT_ID = UUID.fromString("00000000-0000-0000-0000-000000000006");
  public static final UUID FISH_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");
  public static final UUID SHELLFISH_ID = UUID.fromString("00000000-0000-0000-0000-000000000008");
  public static final UUID SESAME_ID = UUID.fromString("00000000-0000-0000-0000-000000000009");

  public DefaultIngredientLookupAdapter() {
    register(new IngredientSummary(PEANUT_ID, "Peanuts (Lạc / Đậu phộng)", true));
    register(new IngredientSummary(MILK_ID, "Cow's Milk (Sữa bò)", true));
    register(new IngredientSummary(EGG_ID, "Eggs (Trứng)", true));
    register(new IngredientSummary(TREE_NUT_ID, "Tree Nuts (Hạt cây)", true));
    register(new IngredientSummary(SOY_ID, "Soybeans (Đậu nành)", true));
    register(new IngredientSummary(WHEAT_ID, "Wheat (Lúa mì / Gluten)", true));
    register(new IngredientSummary(FISH_ID, "Fish (Cá)", true));
    register(new IngredientSummary(SHELLFISH_ID, "Crustacean Shellfish (Hải sản có vỏ)", true));
    register(new IngredientSummary(SESAME_ID, "Sesame (Vừng / Mè)", true));
  }

  public void register(IngredientSummary summary) {
    registry.put(summary.id(), summary);
  }

  @Override
  public Map<UUID, IngredientSummary> findSummariesByIds(Set<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<UUID, IngredientSummary> result = new ConcurrentHashMap<>();
    for (UUID id : ids) {
      IngredientSummary summary = registry.get(id);
      if (summary != null) {
        result.put(id, summary);
      }
    }
    return result;
  }

  @Override
  public List<IngredientSummary> findCommonAllergens() {
    return new ArrayList<>(registry.values());
  }
}
