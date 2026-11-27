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
              "app.chat.llm.base-url=http://localhost:11434/v1",
              "app.chat.llm.api-key=",
              "app.chat.llm.model=qwen2.5:7b",
              "app.chat.llm.connect-timeout=5s",
              "app.chat.llm.read-timeout=60s",
              "app.chat.llm.temperature=0.4",
              "app.chat.llm.max-tokens=1024",
              "app.chat.history-window=12",
              "app.chat.max-context-chars=4500",
              "app.chat.max-message-length=2000",
              "app.chat.rate-limit.per-minute=10",
              "app.chat.rate-limit.per-day=200",
              "app.chat.pending-timeout=120s",
              "app.chat.retention-days=30",
              "app.chat.sse-timeout=60s",
              "app.chat.sse-heartbeat-seconds=15");

  @Test
  void bindsOpenAiCompatibleLlmPropertiesWithoutApiKey() {
    runner.run(context -> {
      assertThat(context).hasNotFailed();
      ChatProperties props = context.getBean(ChatProperties.class);
      assertThat(props.enabled()).isTrue();
      assertThat(props.llm().model()).isEqualTo("qwen2.5:7b");
      assertThat(props.maxContextChars()).isEqualTo(4500);
    });
  }

  @Test
  void rejectsPendingTimeoutShorterThanLlmReadTimeout() {
    runner
        .withPropertyValues("app.chat.pending-timeout=30s")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void historyWindowCannotExceedLimit() {
    runner
        .withPropertyValues("app.chat.history-window=21")
        .run(context -> assertThat(context).hasFailed());
  }

  @EnableConfigurationProperties(ChatProperties.class)
  static class PropertiesConfig {}
}
