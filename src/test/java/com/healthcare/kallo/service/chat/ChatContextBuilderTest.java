package com.healthcare.kallo.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.model.chat.ChatMessage;
import com.healthcare.kallo.model.chat.Conversation;
import com.healthcare.kallo.model.chat.enums.ChatMessageStatus;
import com.healthcare.kallo.model.chat.enums.ChatRole;
import com.healthcare.kallo.repository.chat.ChatMessageRepository;
import com.healthcare.kallo.repository.chat.ConversationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

class ChatContextBuilderTest {
  private final ChatMessageRepository messages = Mockito.mock(ChatMessageRepository.class);
  private final ChatContextBuilder builder = new ChatContextBuilder(messages, userId -> ChatContextData.empty());

  @Test
  void excludesPendingAndFailedRowsAndEndsWithCurrentUser() {
    UUID conversationId = UUID.randomUUID();
    ChatMessage oldUser = message(ChatRole.USER, ChatMessageStatus.COMPLETED, "old user");
    ChatMessage failedAssistant = message(ChatRole.ASSISTANT, ChatMessageStatus.FAILED, null);
    ChatMessage pendingAssistant = message(ChatRole.ASSISTANT, ChatMessageStatus.PENDING, null);
    ChatMessage current = message(ChatRole.USER, ChatMessageStatus.COMPLETED, "current");
    when(messages.findByConversationIdAndStatusOrderByCreatedAtDescIdDesc(any(), any(), any()))
        .thenReturn(List.of(pendingAssistant, failedAssistant, oldUser));

    ChatAiRequest request = builder.build(UUID.randomUUID(), conversationId, current, 3);

    assertThat(request.messages()).extracting(ChatAiRequest.ChatHistoryMessage::content)
        .containsExactly("old user", "current");
    assertThat(request.messages().getLast().role()).isEqualTo("user");
  }

  @Test
  @SuppressWarnings("unchecked")
  void includesConversationSummaryAndTrimsOldMessagesWithinCharBudget() {
    UUID userId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    Conversation conversation = new Conversation();
    conversation.setId(conversationId);
    conversation.setSummary("{\"lastIntent\":\"MEAL_SUGGESTION\"}");

    ConversationRepository convRepo = mock(ConversationRepository.class);
    when(convRepo.findById(conversationId)).thenReturn(Optional.of(conversation));
    ObjectProvider<ConversationRepository> convProvider = mock(ObjectProvider.class);
    when(convProvider.getIfAvailable()).thenReturn(convRepo);

    ChatContextBuilder budgetedBuilder = new ChatContextBuilder(messages, id -> ChatContextData.empty(), convProvider);
    ChatMessage m1 = message(ChatRole.USER, ChatMessageStatus.COMPLETED, "x".repeat(300));
    ChatMessage m2 = message(ChatRole.ASSISTANT, ChatMessageStatus.COMPLETED, "y".repeat(250));
    ChatMessage current = message(ChatRole.USER, ChatMessageStatus.COMPLETED, "z".repeat(200));
    when(messages.findByConversationIdAndStatusOrderByCreatedAtDescIdDesc(any(), any(), any()))
        .thenReturn(List.of(m2, m1));

    ChatAiRequest request = budgetedBuilder.build(userId, conversationId, current, 10, 520);

    assertThat(request.conversationSummary()).contains("MEAL_SUGGESTION");
    assertThat(request.userId()).isEqualTo(userId);
    assertThat(request.messages()).extracting(ChatAiRequest.ChatHistoryMessage::content)
        .containsExactly("y".repeat(250), "z".repeat(200));
  }

  private ChatMessage message(ChatRole role, ChatMessageStatus status, String content) {
    ChatMessage message = new ChatMessage();
    message.setId(UUID.randomUUID());
    message.setRole(role);
    message.setStatus(status);
    message.setContent(content);
    message.setCreatedAt(Instant.now());
    return message;
  }
}
