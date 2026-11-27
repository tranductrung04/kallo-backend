package com.healthcare.kallo.service.chat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.config.ChatProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import com.healthcare.kallo.dto.request.chat.ChatFeedbackRequest;
import com.healthcare.kallo.model.chat.ChatMessage;
import com.healthcare.kallo.model.chat.Conversation;
import com.healthcare.kallo.model.chat.enums.ChatMessageStatus;
import com.healthcare.kallo.model.chat.enums.ChatRole;
import com.healthcare.kallo.repository.chat.ChatMessageRepository;
import com.healthcare.kallo.repository.chat.ConversationRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ChatFeedbackServiceTest {
  private final ChatMessageRepository messages = Mockito.mock(ChatMessageRepository.class);
  private final ConversationRepository conversations = Mockito.mock(ConversationRepository.class);
  private final ChatProperties properties = new ChatProperties(true,
      new ChatProperties.Llm("http://localhost:11434/v1", "", "qwen2.5:7b",
          Duration.ofSeconds(5), Duration.ofSeconds(60), 0.4, 1024),
      12, 4500, 2000, new ChatProperties.RateLimit(10, 200), Duration.ofSeconds(120), 30,
      Duration.ofSeconds(60), 15);
  private final ChatFeedbackService service = new ChatFeedbackService(messages, conversations, properties,
      new ChatMetrics(new SimpleMeterRegistry()));

  @Test
  void rejectsFeedbackOtherThanThumbsUpOrDown() {
    assertThatThrownBy(() -> service.save(UUID.randomUUID(), UUID.randomUUID(), new ChatFeedbackRequest((short) 0)))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void savesFeedbackForOwnedCompletedAssistantMessage() {
    Conversation conversation = new Conversation();
    conversation.setId(UUID.randomUUID());
    ChatMessage message = new ChatMessage();
    message.setId(UUID.randomUUID());
    message.setRole(ChatRole.ASSISTANT);
    message.setStatus(ChatMessageStatus.COMPLETED);
    message.setConversation(conversation);
    when(messages.findById(message.getId())).thenReturn(Optional.of(message));
    when(conversations.findByIdAndUserIdAndDeletedAtIsNull(any(), any())).thenReturn(Optional.of(conversation));

    var result = service.save(UUID.randomUUID(), message.getId(), new ChatFeedbackRequest((short) 1));

    org.assertj.core.api.Assertions.assertThat(result.feedback()).isEqualTo((short) 1);
    org.assertj.core.api.Assertions.assertThat(message.getFeedback()).isEqualTo((short) 1);
  }

  @Test
  void rejectsUserMessage() {
    ChatMessage message = new ChatMessage();
    message.setId(UUID.randomUUID());
    message.setRole(ChatRole.USER);
    message.setStatus(ChatMessageStatus.COMPLETED);
    when(messages.findById(message.getId())).thenReturn(Optional.of(message));

    assertThatThrownBy(() -> service.save(UUID.randomUUID(), message.getId(), new ChatFeedbackRequest((short) 1)))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void rejectsAssistantMessageOwnedByAnotherUser() {
    Conversation conversation = new Conversation();
    conversation.setId(UUID.randomUUID());
    ChatMessage message = new ChatMessage();
    message.setId(UUID.randomUUID());
    message.setRole(ChatRole.ASSISTANT);
    message.setStatus(ChatMessageStatus.COMPLETED);
    message.setConversation(conversation);
    when(messages.findById(message.getId())).thenReturn(Optional.of(message));
    when(conversations.findByIdAndUserIdAndDeletedAtIsNull(any(), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.save(UUID.randomUUID(), message.getId(), new ChatFeedbackRequest((short) 1)))
        .isInstanceOf(BusinessException.class);
  }
}
