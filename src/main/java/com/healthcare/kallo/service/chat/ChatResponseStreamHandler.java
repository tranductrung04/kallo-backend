package com.healthcare.kallo.service.chat;

import com.healthcare.kallo.dto.response.chat.ChatMessageResponse;
import java.util.UUID;

public interface ChatResponseStreamHandler {
  void onMeta(UUID conversationId, UUID userMessageId, UUID assistantMessageId);

  void onDelta(String text);

  void onDone(ChatReplyResponseData response);

  record ChatReplyResponseData(UUID conversationId, ChatMessageResponse userMessage,
      ChatMessageResponse assistantMessage) {}
}
