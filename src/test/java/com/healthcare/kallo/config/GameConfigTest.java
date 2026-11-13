package com.healthcare.kallo.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.kallo.service.game.DailyGoalPolicy;
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
    return new GameProperties(
        new GameProperties.DailyGoal(meals, tolerance),
        new GameProperties.Streak(400),
        new GameProperties.Leaderboard(ZoneId.of("Asia/Ho_Chi_Minh"), false, Duration.ofMillis(500)),
        new GameProperties.Friend(200, 50, Duration.ofHours(24), 20),
        new GameProperties.Search(3, 20),
        new GameProperties.Challenge(Duration.ofDays(1)),
        new GameProperties.Ws(3, List.of()));
  }
}
