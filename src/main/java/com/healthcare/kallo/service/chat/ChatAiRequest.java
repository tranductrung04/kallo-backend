package com.healthcare.kallo.service.chat;

import java.util.List;
import java.util.UUID;

public record ChatAiRequest(UUID conversationId, List<ChatHistoryMessage> messages) {
  public record ChatHistoryMessage(String role, String content) {}

  public String lastUserMessage() {
    if (messages.isEmpty() || !"user".equals(messages.getLast().role())) {
      throw new IllegalArgumentException("Chat request must end with user message");
    }
    return messages.getLast().content();
  }
}
