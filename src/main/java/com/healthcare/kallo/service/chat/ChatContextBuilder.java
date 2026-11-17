package com.healthcare.kallo.service.chat;

import com.healthcare.kallo.model.chat.ChatMessage;
import com.healthcare.kallo.model.chat.enums.ChatMessageStatus;
import com.healthcare.kallo.repository.chat.ChatMessageRepository;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class ChatContextBuilder {
  private final ChatMessageRepository messages;
  private final ChatContextProvider contextProvider;

  public ChatContextBuilder(ChatMessageRepository messages, ChatContextProvider contextProvider) {
    this.messages = messages;
    this.contextProvider = contextProvider;
  }

  public ChatAiRequest build(UUID userId, UUID conversationId, ChatMessage currentUserMessage, int historyWindow) {
    int oldMessageLimit = Math.max(0, historyWindow - 1);
    var history = messages.findByConversationIdAndStatusOrderByCreatedAtDescIdDesc(
        conversationId, ChatMessageStatus.COMPLETED, PageRequest.of(0, historyWindow));
    var ordered = new java.util.ArrayList<>(history.stream()
        .filter(message -> message.getContent() != null)
        .filter(message -> !message.getId().equals(currentUserMessage.getId()))
        .toList().reversed());
    if (ordered.size() > oldMessageLimit) {
      ordered = oldMessageLimit == 0 ? new java.util.ArrayList<>()
          : new java.util.ArrayList<>(ordered.subList(ordered.size() - oldMessageLimit, ordered.size()));
    }
    ordered.add(currentUserMessage);
    ChatAiRequest request = new ChatAiRequest(conversationId, ordered.stream()
        .map(m -> new ChatAiRequest.ChatHistoryMessage(m.getRole().name().toLowerCase(), m.getContent()))
        .toList());
    ChatContextData context = contextProvider.get(userId);
    if (context == null) context = ChatContextData.empty();
    return request.withContext(context);
  }

}
