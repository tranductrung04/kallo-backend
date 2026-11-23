package com.healthcare.kallo.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.model.chat.ChatMessage;
import com.healthcare.kallo.model.chat.enums.ChatMessageStatus;
import com.healthcare.kallo.model.chat.enums.ChatRole;
import com.healthcare.kallo.repository.chat.ChatMessageRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
