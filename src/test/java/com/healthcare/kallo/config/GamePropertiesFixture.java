package com.healthcare.kallo.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

public final class GamePropertiesFixture {
  private GamePropertiesFixture() {}

  public static GameProperties defaults() {
    return new GameProperties(
        new GameProperties.DailyGoal(2, 10),
        new GameProperties.Streak(400),
        new GameProperties.Leaderboard(ZoneId.of("Asia/Ho_Chi_Minh"), false, Duration.ofMillis(500)),
        new GameProperties.Friend(200, 50, Duration.ofHours(24), 20, Duration.ofHours(1), Duration.ofDays(30)),
        new GameProperties.Search(3, 20),
        new GameProperties.Challenge(Duration.ofDays(1)),
        new GameProperties.Ws(3, List.of()));
  }
}
