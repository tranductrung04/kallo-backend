package com.healthcare.kallo.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.kallo.service.game.DailyGoalPolicy;

import static com.healthcare.kallo.config.GamePropertiesFixture.defaults;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class GameConfigTest {
  @Test
  void createsDailyGoalPolicyFromBoundProperties() {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.registerBean(GameProperties.class, () -> properties(3, 15));
      context.register(GameConfig.class);
      context.refresh();

      DailyGoalPolicy policy = context.getBean(DailyGoalPolicy.class);
      assertThat(policy.isMet(3, 850, 1_000)).isTrue();
      assertThat(policy.isMet(3, 840, 1_000)).isFalse();
    }
  }

  private GameProperties properties(int meals, int tolerance) {
    GameProperties defaults = defaults();
    return new GameProperties(
        new GameProperties.DailyGoal(meals, tolerance), defaults.streak(), defaults.leaderboard(),
        defaults.friend(), defaults.search(), defaults.challenge(), defaults.ws());
  }
}
