package com.healthcare.kallo.service.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatPromptBuilderTest {
  private final ChatPromptBuilder builder = new ChatPromptBuilder();

  @Test
  void includesFullHealthContextAndSummaryInSystemPrompt() {
    ChatContextData data = new ChatContextData(
        Map.of("displayName", "Minh", "age", 30, "bmi", 23.8, "dailyCalorieGoal", 1800, "proteinGoalG", 120),
        Map.of("consumedKcal", 1200, "calorieGoal", 1800, "proteinG", 70, "waterMl", 1500),
        List.of("Giáp xác (Tôm, Cua, Ghẹ)"),
        List.of("CRUSTACEANS"),
        List.of(new ChatContextData.ConditionInfo("DIABETES_T2", "Tiểu đường tuýp 2")),
        Map.of("latestWeightKg", 70.5, "delta30dKg", -1.5, "trend", "DOWN", "logCount", 4),
        List.of(new ChatContextData.TodayMealItem("BREAKFAST", UUID.randomUUID(), "Phở bò", 450,
            new BigDecimal("25"), new BigDecimal("50"), new BigDecimal("12"), new BigDecimal("2"))),
        List.of());

    ChatAiRequest request = new ChatAiRequest(
        UUID.randomUUID(),
        List.of(new ChatAiRequest.ChatHistoryMessage("user", "Tối nay ăn gì?")))
        .withUserAndSummary(UUID.randomUUID(), "Người dùng thích món luộc, đang giảm cân.")
        .withContext(data);

    String prompt = builder.buildSystemPrompt(request);

    assertThat(prompt)
        .contains("Minh")
        .contains("1800")
        .contains("Giáp xác (Tôm, Cua, Ghẹ)")
        .contains("Tiểu đường tuýp 2 (DIABETES_T2)")
        .contains("Phở bò (450 kcal)")
        .contains("70.5")
        .contains("Người dùng thích món luộc, đang giảm cân.");
  }
}
