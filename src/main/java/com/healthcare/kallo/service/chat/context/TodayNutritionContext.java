package com.healthcare.kallo.service.chat.context;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TodayNutritionContext(
    LocalDate date,
    BigDecimal consumedKcal,
    BigDecimal proteinG,
    BigDecimal carbsG,
    BigDecimal fatG,
    List<Meal> meals) {
  public TodayNutritionContext {
    meals = meals == null ? List.of() : List.copyOf(meals);
  }

  public record Meal(String mealType, String foodName, BigDecimal quantity, String unit,
      BigDecimal calories, BigDecimal proteinG, BigDecimal carbsG, BigDecimal fatG,
      Instant loggedAt) {}
}
