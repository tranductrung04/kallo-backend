package com.healthcare.kallo.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.ChatProperties;
import com.healthcare.kallo.dto.request.chat.SendMessageRequest;
import com.healthcare.kallo.model.chat.ChatMessage;
import com.healthcare.kallo.model.chat.Conversation;
import com.healthcare.kallo.model.chat.enums.ChatMessageStatus;
import com.healthcare.kallo.model.chat.enums.ChatRole;
import com.healthcare.kallo.repository.chat.ChatMessageRepository;
import com.healthcare.kallo.service.sys.NotificationService;
import com.healthcare.kallo.repository.chat.ConversationRepository;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {
  @Mock ChatMessageRepository messages;
  @Mock ConversationRepository conversations;
  @Mock ChatAiClient ai;
  @Mock ChatContextBuilder context;
  @Mock ChatRateLimiter rateLimiter;
  @Mock ChatTxService tx;
  @Mock NotificationService notifications;

  private final ChatMetrics metrics = new ChatMetrics(new SimpleMeterRegistry());
  private final ChatSafetyService safety = new ChatSafetyService(metrics);
  private ChatService service;
  private final UUID userId = UUID.randomUUID();
  private final UUID conversationId = UUID.randomUUID();
  private final UUID clientMessageId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    ChatProperties properties = new ChatProperties(true,
        new ChatProperties.Ai("http://ai", "key", Duration.ofSeconds(2), Duration.ofSeconds(30), true),
        12, 2000, new ChatProperties.RateLimit(10, 200), Duration.ofMinutes(2), 30,
        Duration.ofSeconds(60), 15);
    service = new ChatService(conversations, messages, ai, context, safety, rateLimiter, properties, tx, metrics,
        notifications);
  }

  @Test
  void completedIdempotentRequestReplaysWithoutCallingAi() {
    ChatMessage user = message(ChatRole.USER, ChatMessageStatus.COMPLETED, "hello");
    ChatMessage assistant = message(ChatRole.ASSISTANT, ChatMessageStatus.COMPLETED, "reply");
    Conversation conversation = conversation();
    user.setConversation(conversation);
    assistant.setConversation(conversation);
    when(tx.saveUserMessage(eq(userId), any(), eq("hello")))
        .thenReturn(new ChatTxService.SavedMessage(conversation, user, assistant, true));

    var result = service.sendMessage(userId, new SendMessageRequest(null, "hello", clientMessageId));

    assertThat(result.assistantMessage().content()).isEqualTo("reply");
    verify(ai, never()).generate(any());
  }

  @Test
  void synchronousReplyDoesNotNotifyConnectedClient() {
    Conversation conversation = conversation();
    ChatMessage user = message(ChatRole.USER, ChatMessageStatus.COMPLETED, "hello");
    ChatMessage assistant = message(ChatRole.ASSISTANT, ChatMessageStatus.PENDING, null);
    user.setConversation(conversation);
    assistant.setConversation(conversation);
    var saved = new ChatTxService.SavedMessage(conversation, user, assistant, false);
    when(tx.saveUserMessage(eq(userId), any(), eq("hello"))).thenReturn(saved);
    when(context.build(any(), any(), any(), eq(12))).thenReturn(new ChatAiRequest(conversationId,
        List.of(new ChatAiRequest.ChatHistoryMessage("user", "hello"))));
    when(ai.generate(any())).thenReturn(new ChatAiReply("reply", List.of(), null, null, "mock"));
    when(tx.completeAssistant(any(), any(), any(), anyLong())).thenAnswer(invocation -> {
      assistant.setContent(invocation.getArgument(1));
      return true;
    });
    var result = service.sendMessage(userId, new SendMessageRequest(conversationId, "hello", clientMessageId));

    assertThat(result.assistantMessage().content()).isEqualTo("reply");
    verify(notifications, never()).notify(any());
  }

  @Test
  void pendingRequestReturnsBusyWithoutCallingAi() {
    when(tx.saveUserMessage(eq(userId), any(), eq("hello")))
        .thenThrow(new BusinessException(ErrorCode.CHAT_CONVERSATION_BUSY));

    assertThatThrownBy(() -> service.sendMessage(userId, new SendMessageRequest(conversationId, "hello", clientMessageId)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.CHAT_CONVERSATION_BUSY);
    verify(ai, never()).generate(any());
  }

  @Test
  void invalidAiOutputIsPersistedAsFailedWithOriginalCode() {
    Conversation conversation = conversation();
    ChatMessage user = message(ChatRole.USER, ChatMessageStatus.COMPLETED, "hello");
    ChatMessage assistant = message(ChatRole.ASSISTANT, ChatMessageStatus.PENDING, null);
    user.setConversation(conversation);
    assistant.setConversation(conversation);
    when(tx.saveUserMessage(eq(userId), any(), eq("hello")))
        .thenReturn(new ChatTxService.SavedMessage(conversation, user, assistant, false));
    when(context.build(any(), any(), any(), eq(12))).thenReturn(new ChatAiRequest(conversationId,
        List.of(new ChatAiRequest.ChatHistoryMessage("user", "hello"))));
    when(ai.generate(any())).thenReturn(new ChatAiReply("", List.of(), null, null, "mock"));

    assertThatThrownBy(() -> service.sendMessage(userId, new SendMessageRequest(conversationId, "hello", clientMessageId)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.CHAT_AI_INVALID_RESPONSE);
    verify(tx).markFailed(assistant, ErrorCode.CHAT_AI_INVALID_RESPONSE.name());
  }

  private Conversation conversation() {
    Conversation conversation = new Conversation();
    conversation.setId(conversationId);
    return conversation;
  }

  private ChatMessage message(ChatRole role, ChatMessageStatus status, String content) {
    ChatMessage message = new ChatMessage();
    message.setId(UUID.randomUUID());
    message.setRole(role);
    message.setStatus(status);
    message.setContent(content);
    return message;
  }
}
