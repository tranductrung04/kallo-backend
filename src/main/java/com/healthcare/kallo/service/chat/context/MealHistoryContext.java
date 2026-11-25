package com.healthcare.kallo.service.chat.context;

import java.util.List;

public record MealHistoryContext(List<TodayNutritionContext.Meal> meals) {
  public MealHistoryContext {
    meals = meals == null ? List.of() : List.copyOf(meals);
  }
}
