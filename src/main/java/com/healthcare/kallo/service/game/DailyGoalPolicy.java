package com.healthcare.kallo.service.game;

public final class DailyGoalPolicy {
  private final int minimumMeals;
  private final int calorieTolerancePercent;

  public DailyGoalPolicy(int minimumMeals, int calorieTolerancePercent) {
    if (minimumMeals < 1) throw new IllegalArgumentException("minimumMeals must be positive");
    if (calorieTolerancePercent < 0 || calorieTolerancePercent > 100) {
      throw new IllegalArgumentException("calorieTolerancePercent must be between 0 and 100");
    }
    this.minimumMeals = minimumMeals;
    this.calorieTolerancePercent = calorieTolerancePercent;
  }

  public boolean isMet(DailyProgress progress) {
    return progress != null
        && isMet(progress.mealsLogged(), progress.caloriesIn(), progress.calorieGoal());
  }

  public boolean isMet(int mealsLogged, Integer caloriesIn, Integer calorieGoal) {
    if (mealsLogged < minimumMeals || mealsLogged < 0
        || caloriesIn == null || caloriesIn < 0 || calorieGoal == null || calorieGoal <= 0) {
      return false;
    }
    long scaledCalories = (long) caloriesIn * 100;
    long lower = (long) calorieGoal * (100 - calorieTolerancePercent);
    long upper = (long) calorieGoal * (100 + calorieTolerancePercent);
    return scaledCalories >= lower && scaledCalories <= upper;
  }
}
