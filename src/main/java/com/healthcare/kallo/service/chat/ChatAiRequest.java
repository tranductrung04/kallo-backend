package com.healthcare.kallo.service.chat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ChatAiRequest(UUID conversationId, String locale, ChatSystemContext systemContext,
    List<ChatHistoryMessage> messages, ChatOptions options) {
  public ChatAiRequest(UUID conversationId, List<ChatHistoryMessage> messages) {
    this(conversationId, "vi", ChatSystemContext.empty(), messages, new ChatOptions(512, false));
  }

  public record ChatHistoryMessage(String role, String content) {}

  public record ChatSystemContext(Map<String, Object> profile, Map<String, Object> today,
      List<String> allergies) {
    public ChatSystemContext {
      profile = profile == null ? Map.of() : Map.copyOf(profile);
      today = today == null ? Map.of() : Map.copyOf(today);
      allergies = allergies == null ? List.of() : List.copyOf(allergies);
    }

    public static ChatSystemContext empty() {
      return new ChatSystemContext(Map.of(), Map.of(), List.of());
    }
  }

  public record ChatOptions(int maxTokens, boolean stream) {}

  public ChatAiRequest withContext(ChatContextData context) {
    return new ChatAiRequest(conversationId, locale,
        new ChatSystemContext(context.profile(), context.today(), context.allergies()), messages, options);
  }

  public ChatAiRequest streaming() {
    return new ChatAiRequest(conversationId, locale, systemContext, messages,
        new ChatOptions(options.maxTokens(), true));
  }

  public String lastUserMessage() {
    if (messages.isEmpty() || !"user".equals(messages.getLast().role())) {
      throw new IllegalArgumentException("Chat request must end with user message");
    }
    return messages.getLast().content();
  }
}
