package com.healthcare.kallo.service.nutri;

import com.healthcare.kallo.dto.response.nutri.AllergicIngredientWarning;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.FoodIngredient;
import com.healthcare.kallo.model.food.Ingredient;
import com.healthcare.kallo.repository.profile.UserAllergyRepository;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class NutriAllergyChecker {

  private final UserAllergyRepository userAllergyRepository;

  public NutriAllergyChecker(UserAllergyRepository userAllergyRepository) {
    this.userAllergyRepository = userAllergyRepository;
  }

  public Set<UUID> loadUserAllergicIngredientIds(UUID userId) {
    return userAllergyRepository.findAllByIdUserId(userId).stream()
        .map(ua -> ua.getId().getIngredientId())
        .collect(Collectors.toSet());
  }

  public List<AllergicIngredientWarning> checkFoodAllergies(
      Food food, Set<UUID> userAllergicIngredientIds) {
    if (food == null || userAllergicIngredientIds == null || userAllergicIngredientIds.isEmpty()) {
      return Collections.emptyList();
    }
    List<FoodIngredient> foodIngredients = food.getIngredients();
    if (foodIngredients == null || foodIngredients.isEmpty()) {
      return Collections.emptyList();
    }
    return foodIngredients.stream()
        .map(FoodIngredient::getIngredient)
        .filter(
            ing ->
                ing != null
                    && ing.isAllergen()
                    && ing.getOwnerUserId() == null
                    && ing.isActive()
                    && userAllergicIngredientIds.contains(ing.getId()))
        .map(this::toWarning)
        .distinct()
        .toList();
  }

  private AllergicIngredientWarning toWarning(Ingredient ingredient) {
    return new AllergicIngredientWarning(
        ingredient.getId(), ingredient.getCode(), ingredient.getName());
  }
}
