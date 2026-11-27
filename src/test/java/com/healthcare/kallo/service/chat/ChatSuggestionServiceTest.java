package com.healthcare.kallo.service.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.kallo.config.ChatProperties;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatSuggestionServiceTest {
  private final ChatProperties properties = new ChatProperties(true,
      new ChatProperties.Llm("http://localhost:11434/v1", "", "qwen2.5:7b",
          Duration.ofSeconds(5), Duration.ofSeconds(60), 0.4, 1024),
      12, 4500, 2000, new ChatProperties.RateLimit(10, 200), Duration.ofSeconds(120), 30,
      Duration.ofSeconds(60), 15);

  @Test
  void usesRemainingCaloriesWhenDailySummaryExists() {
    ChatSuggestionService service = new ChatSuggestionService(userId -> new ChatContextData(
        Map.of(), Map.of("consumedKcal", 1350, "calorieGoal", 1800), java.util.List.of()), properties);

    assertThat(service.get(UUID.randomUUID()).getFirst().reason()).contains("450");
  }

  @Test
  void describesOverGoalCalories() {
    ChatSuggestionService service = new ChatSuggestionService(userId -> new ChatContextData(
        Map.of(), Map.of("consumedKcal", 1950, "calorieGoal", 1800), java.util.List.of()), properties);

    assertThat(service.get(UUID.randomUUID()).getFirst().reason()).contains("vượt mục tiêu 150");
  }
}
