package com.healthcare.kallo.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(
    int devicesPerHour,
    int syncPushPerMinute,
    int syncPullPerMinute,
    int notificationsPerMinute,
    int settingsPerMinute,
    int websocketPerMinute) {
  public RateLimitProperties {
    if (devicesPerHour < 1 || syncPushPerMinute < 1 || syncPullPerMinute < 1
        || notificationsPerMinute < 1 || settingsPerMinute < 1 || websocketPerMinute < 1) {
      throw new IllegalArgumentException("Rate limits must be positive");
    }
  }

  public Duration hour() {
    return Duration.ofHours(1);
  }

  public Duration minute() {
    return Duration.ofMinutes(1);
  }
}
