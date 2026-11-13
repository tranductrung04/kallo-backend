package com.healthcare.kallo.config;

import com.healthcare.kallo.service.auth.RateLimiter;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class RateLimitTestConfig {
  @Bean
  RateLimitProperties rateLimitProperties() {
    return new RateLimitProperties(20, 30, 60, 120, 30, 10);
  }

  @Bean
  GameProperties gameProperties() {
    return new GameProperties(new GameProperties.DailyGoal(2, 10), new GameProperties.Streak(400),
        new GameProperties.Leaderboard(ZoneId.of("Asia/Ho_Chi_Minh"), false, Duration.ofMillis(500)),
        new GameProperties.Friend(200, 50, Duration.ofHours(24), 20, Duration.ofHours(1)),
        new GameProperties.Search(3, 20), new GameProperties.Challenge(Duration.ofDays(1)),
        new GameProperties.Ws(3, List.of()));
  }

  @Bean
  RateLimiter rateLimiter() {
    return (key, limit, window) -> RateLimiter.Result.permit();
  }
}
