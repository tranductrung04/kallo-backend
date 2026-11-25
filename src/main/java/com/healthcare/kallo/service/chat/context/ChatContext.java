package com.healthcare.kallo.service.chat.context;

import java.time.Instant;
import java.util.List;

public record ChatContext(
    UserProfileContext userProfile,
    NutritionGoalsContext nutritionGoals,
    TodayNutritionContext todayNutrition,
    ActivityContext activity,
    FoodPreferenceContext foodPreferences,
    WeightTrendContext healthTrend,
    NutritionHistoryContext sevenDayNutrition,
    LongTermHealthTrendContext thirtyDayTrend,
    ContextMetadata metadata) {

  public static ChatContext empty() {
    return new ChatContext(null, null, null,
        new ActivityContext(null, null, null, List.of(), null),
        new FoodPreferenceContext(List.of(), List.of(), List.of(), null, List.of()),
        null, new NutritionHistoryContext(0, null, null, null, null, null, null, List.of()),
        null, new ContextMetadata("2.0", Instant.EPOCH, "Asia/Ho_Chi_Minh"));
  }
}
