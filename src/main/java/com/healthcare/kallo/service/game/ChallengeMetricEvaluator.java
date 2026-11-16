package com.healthcare.kallo.service.game;

import com.healthcare.kallo.model.game.enums.ChallengeMetric;
import java.util.List;

/** Pure challenge progress calculation. */
public final class ChallengeMetricEvaluator {
  private ChallengeMetricEvaluator() {}

  public static int evaluate(ChallengeMetric metric, List<DailyProgress> progress,
      DailyGoalPolicy dailyGoalPolicy) {
    if (metric == null || progress == null || progress.isEmpty()) return 0;
    long value = switch (metric) {
      case WATER_GOAL_DAYS -> progress.stream()
          .filter(ChallengeMetricEvaluator::waterGoalMet)
          .count();
      case CALORIE_GOAL_DAYS -> progress.stream()
          .filter(dailyGoalPolicy::isMet)
          .count();
      case LOG_DAYS -> progress.stream()
          .filter(valueForLog -> valueForLog != null && valueForLog.mealsLogged() >= 1)
          .count();
      case STEPS_TOTAL -> progress.stream()
          .filter(valueForSteps -> valueForSteps != null)
          .mapToLong(valueForSteps -> Math.max(0, valueForSteps.steps()))
          .sum();
    };
    return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
  }

  private static boolean waterGoalMet(DailyProgress progress) {
    return progress != null && progress.waterGoalMl() != null
        && progress.waterGoalMl() > 0 && progress.waterMl() >= progress.waterGoalMl();
  }
}
