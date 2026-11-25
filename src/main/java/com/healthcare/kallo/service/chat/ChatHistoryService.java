package com.healthcare.kallo.service.chat;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.model.chat.ChatMessage;
import com.healthcare.kallo.model.chat.Conversation;
import com.healthcare.kallo.model.chat.enums.ChatMessageStatus;
import com.healthcare.kallo.repository.chat.ChatMessageRepository;
import com.healthcare.kallo.repository.chat.ConversationRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatHistoryService {
  private final ConversationRepository conversations;
  private final ChatMessageRepository messages;

  public ChatHistoryService(ConversationRepository conversations, ChatMessageRepository messages) {
    this.conversations = conversations;
    this.messages = messages;
  }

  @Transactional(readOnly = true)
  public History load(UUID userId, UUID conversationId, UUID currentMessageId, int historyWindow) {
    Conversation conversation = conversations.findByIdAndUserIdAndDeletedAtIsNull(conversationId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    int limit = Math.max(1, historyWindow);
    List<ChatMessage> rows = messages.findByConversationIdAndStatusOrderByCreatedAtDescIdDesc(
        conversation.getId(), ChatMessageStatus.COMPLETED, PageRequest.of(0, limit));
    List<ChatMessage> ordered = new ArrayList<>(rows.stream()
        .filter(message -> message.getContent() != null)
        .filter(message -> !message.getId().equals(currentMessageId))
        .toList().reversed());
    int oldMessageLimit = Math.max(0, historyWindow - 1);
    if (ordered.size() > oldMessageLimit) {
      ordered = oldMessageLimit == 0 ? new ArrayList<>()
          : new ArrayList<>(ordered.subList(ordered.size() - oldMessageLimit, ordered.size()));
    }
    return new History(conversation.getSummary(), ordered);
  }

  public record History(String summary, List<ChatMessage> messages) {
    public History {
      messages = messages == null ? List.of() : List.copyOf(messages);
    }
  }
}
