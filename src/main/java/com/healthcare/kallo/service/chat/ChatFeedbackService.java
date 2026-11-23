package com.healthcare.kallo.service.chat;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.ChatProperties;
import com.healthcare.kallo.dto.request.chat.ChatFeedbackRequest;
import com.healthcare.kallo.dto.response.chat.ChatFeedbackResponse;
import com.healthcare.kallo.model.chat.ChatMessage;
import com.healthcare.kallo.model.chat.enums.ChatMessageStatus;
import com.healthcare.kallo.model.chat.enums.ChatRole;
import com.healthcare.kallo.repository.chat.ChatMessageRepository;
import com.healthcare.kallo.repository.chat.ConversationRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatFeedbackService {
  private final ChatMessageRepository messages;
  private final ConversationRepository conversations;
  private final ChatProperties properties;
  private final ChatMetrics metrics;

  public ChatFeedbackService(ChatMessageRepository messages, ConversationRepository conversations,
      ChatProperties properties, ChatMetrics metrics) {
    this.messages = messages;
    this.conversations = conversations;
    this.properties = properties;
    this.metrics = metrics;
  }

  @Transactional
  public ChatFeedbackResponse save(UUID userId, UUID messageId, ChatFeedbackRequest request) {
    if (!properties.enabled()) throw new BusinessException(ErrorCode.NOT_FOUND);
    if (request.feedback() != 1 && request.feedback() != -1) {
      throw new BusinessException(ErrorCode.INVALID_PARAMETER);
    }
    ChatMessage message = messages.findById(messageId)
        .filter(candidate -> candidate.getRole() == ChatRole.ASSISTANT)
        .filter(candidate -> candidate.getStatus() == ChatMessageStatus.COMPLETED)
        .filter(candidate -> conversations.findByIdAndUserIdAndDeletedAtIsNull(
            candidate.getConversation().getId(), userId).isPresent())
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    message.setFeedback(request.feedback());
    metrics.recordFeedback(request.feedback() == 1 ? "up" : "down");
    return new ChatFeedbackResponse(messageId, request.feedback());
  }
}
