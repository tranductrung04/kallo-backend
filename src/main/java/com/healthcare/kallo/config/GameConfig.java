package com.healthcare.kallo.config;

import com.healthcare.kallo.service.game.DailyGoalPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GameConfig {
  @Bean
  DailyGoalPolicy dailyGoalPolicy(GameProperties properties) {
    var goal = properties.dailyGoal();
    return new DailyGoalPolicy(goal.minMeals(), goal.calorieTolerancePercent());
  }
}
