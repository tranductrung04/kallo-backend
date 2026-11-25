package com.healthcare.kallo.service.chat;

import com.healthcare.kallo.model.chat.ChatMessage;
import com.healthcare.kallo.model.chat.enums.ChatMessageStatus;
import com.healthcare.kallo.repository.chat.ChatMessageRepository;
import java.util.ArrayList;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class ChatContextBuilder {
  private final ChatContextProvider contextProvider;
  private final ChatHistoryService historyService;
  private final ChatMessageRepository legacyMessages;

  @Autowired
  public ChatContextBuilder(ChatContextProvider contextProvider, ChatHistoryService historyService) {
    this.contextProvider = contextProvider;
    this.historyService = historyService;
    this.legacyMessages = null;
  }

  /** Test-only compatibility constructor. Production path always uses ChatHistoryService. */
  public ChatContextBuilder(ChatMessageRepository messages, ChatContextProvider contextProvider) {
    this.contextProvider = contextProvider;
    this.historyService = null;
    this.legacyMessages = messages;
  }

  public ChatAiRequest build(UUID userId, UUID conversationId, ChatMessage currentUserMessage, int historyWindow) {
    ChatHistoryService.History history = historyService == null
        ? legacyHistory(conversationId, currentUserMessage, historyWindow)
        : historyService.load(userId, conversationId, currentUserMessage.getId(), historyWindow);
    var ordered = new ArrayList<>(history.messages());
    ordered.add(currentUserMessage);
    ChatContextData context = contextProvider.get(userId);
    ChatAiRequest request = new ChatAiRequest(conversationId, ordered.stream()
        .map(m -> new ChatAiRequest.ChatHistoryMessage(m.getRole().name().toLowerCase(), m.getContent()))
        .toList()).withContext(context);
    return history.summary() == null || history.summary().isBlank()
        ? request : request.withHistorySummary(history.summary());
  }

  private ChatHistoryService.History legacyHistory(UUID conversationId, ChatMessage current, int historyWindow) {
    int oldMessageLimit = Math.max(0, historyWindow - 1);
    var rows = legacyMessages.findByConversationIdAndStatusOrderByCreatedAtDescIdDesc(
        conversationId, ChatMessageStatus.COMPLETED, PageRequest.of(0, historyWindow));
    var ordered = new ArrayList<>(rows.stream().filter(message -> message.getContent() != null)
        .filter(message -> !message.getId().equals(current.getId())).toList().reversed());
    if (ordered.size() > oldMessageLimit) {
      ordered = oldMessageLimit == 0 ? new ArrayList<>()
          : new ArrayList<>(ordered.subList(ordered.size() - oldMessageLimit, ordered.size()));
    }
    return new ChatHistoryService.History(null, ordered);
  }
}
