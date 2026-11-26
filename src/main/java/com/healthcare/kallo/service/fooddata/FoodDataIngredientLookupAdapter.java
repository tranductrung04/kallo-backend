package com.healthcare.kallo.service.fooddata;

import com.healthcare.kallo.model.food.Ingredient;
import com.healthcare.kallo.repository.food.IngredientRepository;
import com.healthcare.kallo.service.profile.port.IngredientLookup;
import com.healthcare.kallo.service.profile.port.IngredientSummary;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class FoodDataIngredientLookupAdapter implements IngredientLookup {

  private final IngredientRepository ingredientRepository;

  public FoodDataIngredientLookupAdapter(IngredientRepository ingredientRepository) {
    this.ingredientRepository = ingredientRepository;
  }

  @Override
  public Map<UUID, IngredientSummary> findSummariesByIds(Set<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      return Collections.emptyMap();
    }
    List<Ingredient> ingredients = ingredientRepository.findAllByIds(ids);
    return ingredients.stream()
        .collect(Collectors.toMap(
            Ingredient::getId,
            ing -> new IngredientSummary(ing.getId(), ing.getCode(), ing.getName(), ing.isActive())
        ));
  }

  @Override
  public List<IngredientSummary> findCommonAllergens() {
    List<Ingredient> allergens = ingredientRepository.findAllCommonAllergens();
    return allergens.stream()
        .map(ing -> new IngredientSummary(ing.getId(), ing.getCode(), ing.getName(), ing.isActive()))
        .toList();
  }
}
