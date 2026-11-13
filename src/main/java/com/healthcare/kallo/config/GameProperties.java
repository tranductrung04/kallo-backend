package com.healthcare.kallo.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("app.game")
@Validated
public record GameProperties(
    @Valid @NotNull DailyGoal dailyGoal,
    @Valid @NotNull Streak streak,
    @Valid @NotNull Leaderboard leaderboard,
    @Valid @NotNull Friend friend,
    @Valid @NotNull Search search,
    @Valid @NotNull Challenge challenge,
    @Valid @NotNull Ws ws) {

  public record DailyGoal(
      @Min(1) int minMeals,
      @Min(0) @Max(100) int calorieTolerancePercent) {}

  public record Streak(@Min(1) @Max(400) int windowDays) {}

  public record Leaderboard(
      @NotNull ZoneId zone,
      boolean stepsEnabled,
      @NotNull Duration pushDebounce) {
    public Leaderboard {
      requirePositive(pushDebounce, "leaderboard.push-debounce");
    }
  }

  public record Friend(
      @Min(1) @Max(200) int maxFriends,
      @Min(1) @Max(50) int maxPendingOut,
      @NotNull Duration declineCooldown,
      @Min(1) int requestsPerHour,
      @NotNull Duration requestWindow) {
    public Friend {
      requirePositive(declineCooldown, "friend.decline-cooldown");
      requirePositive(requestWindow, "friend.request-window");
    }
  }

  public record Search(@Min(1) int minLength, @Min(1) @Max(50) int maxResults) {}

  public record Challenge(@NotNull Duration expiryGrace) {
    public Challenge {
      requirePositive(expiryGrace, "challenge.expiry-grace");
    }
  }

  public record Ws(@Min(1) @Max(3) int maxSessionsPerUser, List<String> allowedOrigins) {
    public Ws {
      allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
  }

  private static void requirePositive(Duration value, String name) {
    if (value == null || value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException(name + " must be positive");
    }
  }
}
