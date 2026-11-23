package com.healthcare.kallo.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ChatPropertiesTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(PropertiesConfig.class)
          .withPropertyValues(
              "app.chat.enabled=true",
              "app.chat.ai.base-url=http://localhost:8000",
              "app.chat.ai.api-key=",
              "app.chat.ai.connect-timeout=2s",
              "app.chat.ai.read-timeout=30s",
              "app.chat.ai.mock-enabled=true",
              "app.chat.history-window=12",
              "app.chat.max-message-length=2000",
              "app.chat.rate-limit.per-minute=10",
              "app.chat.rate-limit.per-day=200",
              "app.chat.pending-timeout=120s",
              "app.chat.retention-days=30",
              "app.chat.sse-timeout=60s",
              "app.chat.sse-heartbeat-seconds=15");

  @Test
  void mockAiMayOmitApiKey() {
    runner.run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  void realAiMayStartWithUnavailableFallbackBeforePhaseTwoClient() {
    runner
        .withPropertyValues("app.chat.ai.mock-enabled=false")
        .run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  void historyWindowCannotExceedAiContextLimit() {
    runner
        .withPropertyValues("app.chat.history-window=13")
        .run(context -> assertThat(context).hasFailed());
  }

  @EnableConfigurationProperties(ChatProperties.class)
  static class PropertiesConfig {}
}
