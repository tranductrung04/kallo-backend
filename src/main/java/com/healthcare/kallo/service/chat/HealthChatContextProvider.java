package com.healthcare.kallo.service.chat;

import com.healthcare.kallo.service.chat.context.ActivityContext;
import com.healthcare.kallo.service.chat.context.ChatContext;
import com.healthcare.kallo.service.chat.context.FoodPreferenceContext;
import com.healthcare.kallo.service.chat.context.NutritionGoalsContext;
import com.healthcare.kallo.service.chat.context.TodayNutritionContext;
import com.healthcare.kallo.service.chat.context.UserProfileContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class HealthChatContextProvider implements ChatContextProvider {
  private final ChatContextService contextService;

  public HealthChatContextProvider(ChatContextService contextService) {
    this.contextService = contextService;
  }

  @Override
  public ChatContextData get(UUID userId) {
    ChatContext context = contextService.get(userId);
    Map<String, Object> profile = new LinkedHashMap<>();
    UserProfileContext user = context.userProfile();
    if (user != null) {
      put(profile, "age", user.age());
      put(profile, "gender", user.gender());
      put(profile, "heightCm", user.heightCm());
      put(profile, "currentWeightKg", user.currentWeightKg());
      put(profile, "targetWeightKg", user.targetWeightKg());
      put(profile, "activityLevel", user.activityLevel());
    }
    NutritionGoalsContext goals = context.nutritionGoals();
    if (goals != null) {
      Map<String, Object> goalMap = new LinkedHashMap<>();
      put(goalMap, "dailyCalories", goals.dailyCalories());
      put(goalMap, "tdee", goals.tdee());
      put(goalMap, "proteinG", goals.proteinG());
      put(goalMap, "carbsG", goals.carbsG());
      put(goalMap, "fatG", goals.fatG());
      put(goalMap, "waterMl", goals.waterMl());
      profile.put("nutritionGoals", goalMap);
      profile.putAll(goalMap);
    }
    if (context.healthTrend() != null) profile.put("weightTrend", context.healthTrend());
    profile.put("foodPreferences", context.foodPreferences());
    if (context.sevenDayNutrition() != null) profile.put("sevenDayNutrition", context.sevenDayNutrition());
    if (context.thirtyDayTrend() != null) profile.put("thirtyDayTrend", context.thirtyDayTrend());

    Map<String, Object> today = new LinkedHashMap<>();
    Map<String, Object> nutritionMap = new LinkedHashMap<>();
    TodayNutritionContext nutrition = context.todayNutrition();
    if (nutrition != null) {
      put(nutritionMap, "date", nutrition.date());
      put(nutritionMap, "consumedKcal", nutrition.consumedKcal());
      put(nutritionMap, "proteinG", nutrition.proteinG());
      put(nutritionMap, "carbsG", nutrition.carbsG());
      put(nutritionMap, "fatG", nutrition.fatG());
      put(nutritionMap, "meals", nutrition.meals());
      today.put("nutrition", nutritionMap);
      today.putAll(nutritionMap);
    }
    ActivityContext activity = context.activity();
    if (activity != null) {
      put(today, "steps", activity.steps());
      put(today, "activeCalories", activity.activeCalories());
      put(today, "exerciseMinutes", activity.exerciseMinutes());
      put(today, "activities", activity.activities());
      put(today, "waterMl", activity.waterMl());
    }
    List<String> allergyNames = context.foodPreferences().allergies().stream()
        .map(FoodPreferenceContext.Allergy::name).toList();
    return new ChatContextData(profile, today, allergyNames);
  }

  private void put(Map<String, Object> map, String key, Object value) {
    if (value != null) map.put(key, value);
  }
}
