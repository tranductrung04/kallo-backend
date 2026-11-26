package com.healthcare.kallo.service.chat;

import com.healthcare.kallo.model.chat.ChatMessage;
import com.healthcare.kallo.model.chat.Conversation;
import com.healthcare.kallo.model.chat.enums.ChatMessageStatus;
import com.healthcare.kallo.repository.chat.ChatMessageRepository;
import com.healthcare.kallo.repository.chat.ConversationRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatSummaryService {
  private static final Logger log = LoggerFactory.getLogger(ChatSummaryService.class);
  private static final int MIN_MESSAGES_FOR_SUMMARY = 20;

  private final ConversationRepository conversations;
  private final ChatMessageRepository messages;
  private final ChatAiClient aiClient;

  public ChatSummaryService(
      ConversationRepository conversations,
      ChatMessageRepository messages,
      ChatAiClient aiClient) {
    this.conversations = conversations;
    this.messages = messages;
    this.aiClient = aiClient;
  }

  @Async("chatTaskExecutor")
  @Transactional
  public void summarizeIfNeeded(UUID conversationId, int historyWindow) {
    if (conversationId == null) return;
    try {
      Conversation conversation = conversations.findById(conversationId).orElse(null);
      if (conversation == null || conversation.getDeletedAt() != null) return;
      int triggerThreshold = Math.max(MIN_MESSAGES_FOR_SUMMARY, historyWindow * 2);
      if (conversation.getMessageCount() < triggerThreshold) return;

      List<ChatMessage> completed = messages.findByConversationIdOrderByCreatedAtAscIdAsc(conversationId).stream()
          .filter(m -> m.getStatus() == ChatMessageStatus.COMPLETED && m.getContent() != null)
          .toList();
      if (completed.size() <= historyWindow) return;

      int cutoff = completed.size() - historyWindow;
      List<ChatAiRequest.ChatHistoryMessage> older = completed.subList(0, cutoff).stream()
          .map(m -> new ChatAiRequest.ChatHistoryMessage(m.getRole().name().toLowerCase(), m.getContent()))
          .toList();

      String updated = aiClient.summarize(conversation.getSummary(), older);
      if (updated != null && !updated.isBlank()) {
        conversations.updateSummary(conversationId, updated.trim());
      }
    } catch (RuntimeException exception) {
      log.warn("Failed to refresh rolling chat summary conversationId={}", conversationId, exception);
    }
  }
}
