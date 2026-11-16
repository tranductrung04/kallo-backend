package com.healthcare.kallo.dto.response.chat;

import com.healthcare.kallo.model.chat.Conversation;
import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
    UUID id,
    String title,
    int messageCount,
    Instant lastMessageAt,
    Instant createdAt,
    Instant updatedAt) {
  public static ConversationResponse from(Conversation conversation) {
    return new ConversationResponse(conversation.getId(), conversation.getTitle(), conversation.getMessageCount(),
        conversation.getLastMessageAt(), conversation.getCreatedAt(), conversation.getUpdatedAt());
  }
}
