package com.healthcare.kallo.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.ChatProperties;
import com.healthcare.kallo.dto.request.chat.SendMessageRequest;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.chat.ChatMessage;
import com.healthcare.kallo.model.chat.Conversation;
import com.healthcare.kallo.model.chat.enums.ChatMessageStatus;
import com.healthcare.kallo.model.chat.enums.ChatRole;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.chat.ChatMessageRepository;
import com.healthcare.kallo.repository.chat.ConversationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ChatTxServiceTest {
  @Mock ConversationRepository conversations;
  @Mock ChatMessageRepository messages;
  @Mock UserRepository users;
  private ChatTxService tx;
  private final UUID userId = UUID.randomUUID();
  private final UUID conversationId = UUID.randomUUID();
  private final UUID clientMessageId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    ChatProperties properties = new ChatProperties(true,
        new ChatProperties.Ai("http://ai", "key", Duration.ofSeconds(2), Duration.ofSeconds(30), true),
        12, 2000, new ChatProperties.RateLimit(10, 200), Duration.ofMinutes(2), 30);
    tx = new ChatTxService(conversations, messages, users, properties);
  }

  @Test
  void stalePendingIsFailedAndNewMessageCanStart() {
    Conversation conversation = conversation();
    ChatMessage stale = message(ChatMessageStatus.PENDING, null);
    stale.setCreatedAt(Instant.now().minus(Duration.ofMinutes(3)));
    ChatMessage user = message(ChatMessageStatus.COMPLETED, "hello");
    user.setCreatedAt(Instant.now());
    ChatMessage assistant = message(ChatMessageStatus.PENDING, null);
    assistant.setCreatedAt(Instant.now());
    when(conversations.findByIdAndUserIdAndDeletedAtIsNull(any(), any())).thenReturn(Optional.of(conversation));
    when(messages.findByConversationIdAndClientMessageId(any(), any())).thenReturn(Optional.empty());
    when(messages.findByConversationIdAndStatus(any(), eq(ChatMessageStatus.PENDING))).thenReturn(Optional.of(stale));
    when(messages.saveAndFlush(any(ChatMessage.class))).thenAnswer(invocation -> {
      ChatMessage message = invocation.getArgument(0);
      if (message.getCreatedAt() == null) message.setCreatedAt(Instant.now());
      return message;
    });

    var saved = tx.saveUserMessage(userId, new SendMessageRequest(conversationId, "hello", clientMessageId), "hello");

    assertThat(saved.alreadyAnswered()).isFalse();
    verify(messages).failPending(eq(stale.getId()), any(Map.class));
  }

  @Test
  void freshPendingReturnsBusy() {
    Conversation conversation = conversation();
    ChatMessage pending = message(ChatMessageStatus.PENDING, null);
    pending.setCreatedAt(Instant.now());
    when(conversations.findByIdAndUserIdAndDeletedAtIsNull(any(), any())).thenReturn(Optional.of(conversation));
    when(messages.findByConversationIdAndClientMessageId(any(), any())).thenReturn(Optional.empty());
    when(messages.findByConversationIdAndStatus(any(), eq(ChatMessageStatus.PENDING))).thenReturn(Optional.of(pending));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> tx.saveUserMessage(
        userId, new SendMessageRequest(conversationId, "hello", clientMessageId), "hello"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.CHAT_CONVERSATION_BUSY);
  }

  private Conversation conversation() {
    Conversation conversation = new Conversation();
    conversation.setId(conversationId);
    conversation.setMessageCount(0);
    return conversation;
  }

  private ChatMessage message(ChatMessageStatus status, String content) {
    ChatMessage message = new ChatMessage();
    message.setId(UUID.randomUUID());
    message.setRole(status == ChatMessageStatus.PENDING ? ChatRole.ASSISTANT : ChatRole.USER);
    message.setStatus(status);
    message.setContent(content);
    message.setConversation(conversation());
    return message;
  }
}
