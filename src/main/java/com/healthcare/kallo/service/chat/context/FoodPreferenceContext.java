package com.healthcare.kallo.service.chat.context;

import java.util.List;

public record FoodPreferenceContext(
    List<Allergy> allergies,
    List<String> dietaryPreferences,
    List<String> dislikedFoods,
    Integer budgetPerDay,
    List<String> preferredCuisines) {
  public FoodPreferenceContext {
    allergies = allergies == null ? List.of() : List.copyOf(allergies);
    dietaryPreferences = dietaryPreferences == null ? List.of() : List.copyOf(dietaryPreferences);
    dislikedFoods = dislikedFoods == null ? List.of() : List.copyOf(dislikedFoods);
    preferredCuisines = preferredCuisines == null ? List.of() : List.copyOf(preferredCuisines);
  }

  public record Allergy(String name, String severity, String note) {}
}
