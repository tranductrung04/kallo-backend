package com.healthcare.kallo.service.chat;

import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.service.act.ProfileBodyMetrics;
import com.healthcare.kallo.service.game.DailyProgress;
import com.healthcare.kallo.service.game.DailyProgressProvider;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class HealthChatContextProvider implements ChatContextProvider {
  private final UserRepository users;
  private final ObjectProvider<ProfileBodyMetrics> bodyMetrics;
  private final ObjectProvider<DailyProgressProvider> dailyProgress;
  private final Clock clock;

  public HealthChatContextProvider(UserRepository users, ObjectProvider<ProfileBodyMetrics> bodyMetrics,
      ObjectProvider<DailyProgressProvider> dailyProgress, Clock clock) {
    this.users = users;
    this.bodyMetrics = bodyMetrics;
    this.dailyProgress = dailyProgress;
    this.clock = clock;
  }

  @Override
  public ChatContextData get(UUID userId) {
    User user = users.findById(userId).orElse(null);
    if (user == null) return ChatContextData.empty();
    ZoneId zone = ZoneId.of(user.getTimezone());
    LocalDate today = LocalDate.now(clock.withZone(zone));

    Map<String, Object> profile = new LinkedHashMap<>();
    profile.put("displayName", user.getDisplayName());
    profile.put("timezone", user.getTimezone());
    ProfileBodyMetrics metrics = bodyMetrics.getIfAvailable();
    if (metrics != null) {
      metrics.heightCm(userId).ifPresent(value -> profile.put("heightCm", decimal(value)));
      metrics.weightKg(userId).ifPresent(value -> profile.put("weightKg", decimal(value)));
    }

    Map<String, Object> todaySummary = new LinkedHashMap<>();
    DailyProgress progress = dailyProgress.getIfAvailable() == null ? null
        : dailyProgress.getIfAvailable().find(userId, today).orElse(null);
    if (progress != null) {
      todaySummary.put("date", progress.localDate().toString());
      todaySummary.put("mealsLogged", progress.mealsLogged());
      put(todaySummary, "consumedKcal", progress.caloriesIn());
      put(todaySummary, "calorieGoal", progress.calorieGoal());
      todaySummary.put("waterMl", progress.waterMl());
      put(todaySummary, "waterGoalMl", progress.waterGoalMl());
      todaySummary.put("steps", progress.steps());
      put(todaySummary, "caloriesBurned", progress.caloriesBurned());
    }
    return new ChatContextData(profile, todaySummary, List.of());
  }

  private Object decimal(BigDecimal value) {
    return value.stripTrailingZeros().scale() <= 0 ? value.intValue() : value;
  }

  private void put(Map<String, Object> target, String key, Object value) {
    if (value != null) target.put(key, value);
  }
}
