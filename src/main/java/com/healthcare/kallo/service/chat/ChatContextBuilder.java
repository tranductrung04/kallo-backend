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

  public ChatContextBuilder(ChatMessageRepository messages) {
    this.messages = messages;
  }

  public ChatAiRequest build(UUID conversationId, ChatMessage currentUserMessage, int historyWindow) {
    int oldMessageLimit = Math.max(0, historyWindow - 1);
    var history = messages.findByConversationIdAndStatusOrderByCreatedAtDescIdDesc(
        conversationId, ChatMessageStatus.COMPLETED, PageRequest.of(0, historyWindow));
    var ordered = new java.util.ArrayList<>(history.stream()
        .filter(message -> message.getContent() != null)
        .filter(message -> !message.getId().equals(currentUserMessage.getId()))
        .toList().reversed());
    if (ordered.size() > oldMessageLimit) {
      ordered = new java.util.ArrayList<>(ordered.subList(ordered.size() - oldMessageLimit, ordered.size()));
    }
    ordered.add(currentUserMessage);
    return new ChatAiRequest(conversationId, ordered.stream()
        .map(m -> new ChatAiRequest.ChatHistoryMessage(m.getRole().name().toLowerCase(), m.getContent()))
        .toList());
  }
}
