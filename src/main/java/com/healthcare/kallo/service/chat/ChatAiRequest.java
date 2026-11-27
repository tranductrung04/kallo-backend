package com.healthcare.kallo.service.chat;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ChatAiRequest(
    UUID userId,
    UUID conversationId,
    String locale,
    String conversationSummary,
    ChatSystemContext systemContext,
    List<ChatHistoryMessage> messages,
    ChatOptions options) {

  public ChatAiRequest(UUID conversationId, List<ChatHistoryMessage> messages) {
    this(null, conversationId, "vi", null, ChatSystemContext.empty(), messages, new ChatOptions(512, false));
  }

  public ChatAiRequest(
      UUID conversationId,
      String locale,
      ChatSystemContext systemContext,
      List<ChatHistoryMessage> messages,
      ChatOptions options) {
    this(null, conversationId, locale, null, systemContext, messages, options);
  }

  public record ChatHistoryMessage(String role, String content) {}

  public record ChatSystemContext(
      Map<String, Object> profile,
      Map<String, Object> today,
      List<String> allergies,
      List<String> allergyCodes,
      List<ChatContextData.ConditionInfo> chronicConditions,
      Map<String, Object> recentWeight,
      List<ChatContextData.TodayMealItem> todayMeals) {

    public ChatSystemContext {
      profile = profile == null ? Map.of() : Map.copyOf(profile);
      today = today == null ? Map.of() : Map.copyOf(today);
      allergies = allergies == null ? List.of() : List.copyOf(allergies);
      allergyCodes = allergyCodes == null ? List.of() : List.copyOf(allergyCodes);
      chronicConditions = chronicConditions == null ? List.of() : List.copyOf(chronicConditions);
      recentWeight = recentWeight == null ? Map.of() : Map.copyOf(recentWeight);
      todayMeals = todayMeals == null ? List.of() : List.copyOf(todayMeals);
    }

    public ChatSystemContext(Map<String, Object> profile, Map<String, Object> today, List<String> allergies) {
      this(profile, today, allergies, List.of(), List.of(), Map.of(), List.of());
    }

    public static ChatSystemContext empty() {
      return new ChatSystemContext(Map.of(), Map.of(), List.of(), List.of(), List.of(), Map.of(), List.of());
    }

    public List<String> allAllergyTerms() {
      if (allergyCodes.isEmpty()) return allergies;
      LinkedHashSet<String> combined = new LinkedHashSet<>(allergies);
      combined.addAll(allergyCodes);
      return List.copyOf(combined);
    }
  }

  public record ChatOptions(int maxTokens, boolean stream) {}

  public ChatAiRequest withUserAndSummary(UUID targetUserId, String summary) {
    return new ChatAiRequest(targetUserId, conversationId, locale, summary, systemContext, messages, options);
  }

  public ChatAiRequest withContext(ChatContextData context) {
    Map<String, Object> profile = new java.util.LinkedHashMap<>(context.profile());
    if (!context.allergies().isEmpty()) profile.put("allergies", context.allergies());
    return new ChatAiRequest(
        userId,
        conversationId,
        locale,
        conversationSummary,
        new ChatSystemContext(
            profile,
            context.today(),
            context.allergies(),
            context.allergyCodes(),
            context.chronicConditions(),
            context.recentWeight(),
            context.todayMeals()),
        messages,
        options);
  }

  public ChatAiRequest streaming() {
    return new ChatAiRequest(
        userId,
        conversationId,
        locale,
        conversationSummary,
        systemContext,
        messages,
        new ChatOptions(options.maxTokens(), true));
  }

  public String lastUserMessage() {
    if (messages.isEmpty() || !"user".equals(messages.getLast().role())) {
      throw new IllegalArgumentException("Chat request must end with user message");
    }
    return messages.getLast().content();
  }
}
