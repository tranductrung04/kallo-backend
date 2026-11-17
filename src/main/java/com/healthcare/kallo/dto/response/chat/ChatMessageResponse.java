package com.healthcare.kallo.dto.response.chat;

import com.healthcare.kallo.model.chat.ChatMessage;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ChatMessageResponse(
    UUID id,
    String role,
    String content,
    String status,
    UUID clientMessageId,
    Map<String, Object> metadata,
    Short feedback,
    Instant createdAt) {
  public static ChatMessageResponse from(ChatMessage message) {
    return new ChatMessageResponse(message.getId(), message.getRole().name(), message.getContent(),
        message.getStatus().name(), message.getClientMessageId(), message.getMetadata(), message.getFeedback(),
        message.getCreatedAt());
  }
}
