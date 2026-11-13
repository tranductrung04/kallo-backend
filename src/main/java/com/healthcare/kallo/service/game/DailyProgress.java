package com.healthcare.kallo.service.game;

import java.time.LocalDate;

public record DailyProgress(
    LocalDate localDate,
    int mealsLogged,
    Integer caloriesIn,
    Integer calorieGoal,
    int waterMl,
    Integer waterGoalMl,
    int steps,
    Integer caloriesBurned) {}
