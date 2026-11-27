package com.healthcare.kallo.service.chat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ChatContextData(
    Map<String, Object> profile,
    Map<String, Object> today,
    List<String> allergies,
    List<String> allergyCodes,
    List<ConditionInfo> chronicConditions,
    Map<String, Object> recentWeight,
    List<TodayMealItem> todayMeals) {

  public ChatContextData {
    profile = profile == null ? Map.of() : Map.copyOf(profile);
    today = today == null ? Map.of() : Map.copyOf(today);
    allergies = allergies == null ? List.of() : List.copyOf(allergies);
    allergyCodes = allergyCodes == null ? List.of() : List.copyOf(allergyCodes);
    chronicConditions = chronicConditions == null ? List.of() : List.copyOf(chronicConditions);
    recentWeight = recentWeight == null ? Map.of() : Map.copyOf(recentWeight);
    todayMeals = todayMeals == null ? List.of() : List.copyOf(todayMeals);
  }

  public ChatContextData(Map<String, Object> profile, Map<String, Object> today, List<String> allergies) {
    this(profile, today, allergies, List.of(), List.of(), Map.of(), List.of());
  }

  public static ChatContextData empty() {
    return new ChatContextData(Map.of(), Map.of(), List.of(), List.of(), List.of(), Map.of(), List.of());
  }

  public record ConditionInfo(String code, String name) {}

  public record TodayMealItem(
      String mealType,
      UUID foodId,
      String foodName,
      Integer calories,
      BigDecimal proteinG,
      BigDecimal carbsG,
      BigDecimal fatG,
      BigDecimal sugarG) {}
}
