package com.healthcare.kallo.service.chat;

import com.healthcare.kallo.model.chat.ChatMessage;
import com.healthcare.kallo.model.chat.Conversation;
import com.healthcare.kallo.model.chat.enums.ChatMessageStatus;
import com.healthcare.kallo.repository.chat.ChatMessageRepository;
import com.healthcare.kallo.repository.chat.ConversationRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class ChatContextBuilder {
  private static final int DEFAULT_MAX_CONTEXT_CHARS = 12_000;

  private final ChatMessageRepository messages;
  private final ChatContextProvider contextProvider;
  private final ObjectProvider<ConversationRepository> conversations;

  public ChatContextBuilder(ChatMessageRepository messages, ChatContextProvider contextProvider) {
    this(messages, contextProvider, null);
  }

  @Autowired
  public ChatContextBuilder(
      ChatMessageRepository messages,
      ChatContextProvider contextProvider,
      ObjectProvider<ConversationRepository> conversations) {
    this.messages = messages;
    this.contextProvider = contextProvider;
    this.conversations = conversations;
  }

  public ChatAiRequest build(UUID userId, UUID conversationId, ChatMessage currentUserMessage, int historyWindow) {
    return build(userId, conversationId, currentUserMessage, historyWindow, DEFAULT_MAX_CONTEXT_CHARS);
  }

  public ChatAiRequest build(
      UUID userId,
      UUID conversationId,
      ChatMessage currentUserMessage,
      int historyWindow,
      int maxContextChars) {
    int oldMessageLimit = Math.max(0, historyWindow - 1);
    var history = messages.findByConversationIdAndStatusOrderByCreatedAtDescIdDesc(
        conversationId, ChatMessageStatus.COMPLETED, PageRequest.of(0, historyWindow));
    var ordered = new ArrayList<>(history.stream()
        .filter(message -> message.getContent() != null)
        .filter(message -> !message.getId().equals(currentUserMessage.getId()))
        .toList().reversed());
    if (ordered.size() > oldMessageLimit) {
      ordered = oldMessageLimit == 0
          ? new ArrayList<>()
          : new ArrayList<>(ordered.subList(ordered.size() - oldMessageLimit, ordered.size()));
    }

    String summary = resolveConversationSummary(conversationId);
    ChatContextData context = contextProvider.get(userId);
    if (context == null) context = ChatContextData.empty();

    int budget = Math.max(500, maxContextChars);
    int fixedChars = estimateContextChars(context, summary) + safeLength(currentUserMessage.getContent());
    int remainingBudget = Math.max(0, budget - fixedChars);
    List<ChatMessage> trimmedHistory = trimHistoryToBudget(ordered, remainingBudget);

    List<ChatAiRequest.ChatHistoryMessage> requestMessages = new ArrayList<>(trimmedHistory.size() + 1);
    for (ChatMessage m : trimmedHistory) {
      requestMessages.add(new ChatAiRequest.ChatHistoryMessage(m.getRole().name().toLowerCase(), m.getContent()));
    }
    requestMessages.add(new ChatAiRequest.ChatHistoryMessage(
        currentUserMessage.getRole().name().toLowerCase(), currentUserMessage.getContent()));

    return new ChatAiRequest(conversationId, requestMessages)
        .withUserAndSummary(userId, summary)
        .withContext(context);
  }

  private List<ChatMessage> trimHistoryToBudget(List<ChatMessage> ordered, int budget) {
    if (ordered.isEmpty() || budget <= 0) return List.of();
    int total = 0;
    int startIndex = ordered.size();
    for (int i = ordered.size() - 1; i >= 0; i--) {
      int len = safeLength(ordered.get(i).getContent());
      if (total + len > budget) break;
      total += len;
      startIndex = i;
    }
    return ordered.subList(startIndex, ordered.size());
  }

  private String resolveConversationSummary(UUID conversationId) {
    ConversationRepository repo = conversations == null ? null : conversations.getIfAvailable();
    if (repo == null || conversationId == null) return null;
    return repo.findById(conversationId)
        .map(Conversation::getSummary)
        .orElse(null);
  }

  private int estimateContextChars(ChatContextData context, String summary) {
    int size = safeLength(summary);
    size += context.profile().toString().length();
    size += context.today().toString().length();
    size += context.allergies().toString().length();
    size += context.chronicConditions().toString().length();
    size += context.todayMeals().size() * 60;
    return size;
  }

  private int safeLength(String value) {
    return value == null ? 0 : value.length();
  }
}
