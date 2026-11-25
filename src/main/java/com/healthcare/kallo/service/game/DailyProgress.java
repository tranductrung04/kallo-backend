package com.healthcare.kallo.service.game;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyProgress(
    LocalDate localDate,
    int mealsLogged,
    Integer caloriesIn,
    Integer calorieGoal,
    int waterMl,
    Integer waterGoalMl,
    int steps,
    Integer caloriesBurned,
    BigDecimal proteinG,
    BigDecimal carbsG,
    BigDecimal fatG) {
  public DailyProgress(LocalDate localDate, int mealsLogged, Integer caloriesIn, Integer calorieGoal,
      int waterMl, Integer waterGoalMl, int steps, Integer caloriesBurned) {
    this(localDate, mealsLogged, caloriesIn, calorieGoal, waterMl, waterGoalMl, steps, caloriesBurned,
        null, null, null);
  }
}
