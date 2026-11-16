package com.healthcare.kallo.service.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.kallo.model.game.enums.ChallengeMetric;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChallengeMetricEvaluatorTest {
  private final DailyGoalPolicy goals = new DailyGoalPolicy(2, 10);

  @Test
  void evaluatesAllPhaseFiveMetrics() {
    var progress = List.of(
        new DailyProgress(LocalDate.of(2026, 11, 16), 1, 1_000, 2_000, 2_000, 2_000, 1_200, null),
        new DailyProgress(LocalDate.of(2026, 11, 17), 2, 2_000, 2_000, 1_000, 2_000, 2_300, null),
        new DailyProgress(LocalDate.of(2026, 11, 18), 2, 2_100, 2_000, 2_500, 2_000, 3_000, null));

    assertThat(ChallengeMetricEvaluator.evaluate(ChallengeMetric.WATER_GOAL_DAYS, progress, goals)).isEqualTo(2);
    assertThat(ChallengeMetricEvaluator.evaluate(ChallengeMetric.CALORIE_GOAL_DAYS, progress, goals)).isEqualTo(2);
    assertThat(ChallengeMetricEvaluator.evaluate(ChallengeMetric.LOG_DAYS, progress, goals)).isEqualTo(3);
    assertThat(ChallengeMetricEvaluator.evaluate(ChallengeMetric.STEPS_TOTAL, progress, goals)).isEqualTo(6_500);
  }

  @Test
  void ignoresNullAndInvalidWaterGoals() {
    var progress = Arrays.asList(
        new DailyProgress(LocalDate.now(), 1, null, null, 5_000, 0, -3, null),
        null);

    assertThat(ChallengeMetricEvaluator.evaluate(ChallengeMetric.WATER_GOAL_DAYS, progress, goals)).isZero();
    assertThat(ChallengeMetricEvaluator.evaluate(ChallengeMetric.STEPS_TOTAL, progress, goals)).isZero();
  }
}
