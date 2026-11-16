package com.healthcare.kallo.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.healthcare.kallo.act.ActIntegrationTestBase;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.chat.ChatMessage;
import com.healthcare.kallo.model.chat.Conversation;
import com.healthcare.kallo.model.chat.enums.ChatMessageStatus;
import com.healthcare.kallo.model.chat.enums.ChatRole;
import com.healthcare.kallo.repository.chat.ChatMessageRepository;
import com.healthcare.kallo.repository.chat.ConversationRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

class ChatRepositoryIntegrationTest extends ActIntegrationTestBase {
  @Autowired ConversationRepository conversations;
  @Autowired ChatMessageRepository messages;

  @Test
  void constraintsProtectInitialAndPendingIdempotency() {
    User user = verifiedUser("chat-repository-constraints");
    UUID clientId = UUID.randomUUID();
    Conversation first = conversation(user, clientId);
    conversations.saveAndFlush(first);

    Conversation duplicate = conversation(user, clientId);
    assertThatThrownBy(() -> conversations.saveAndFlush(duplicate))
        .isInstanceOf(DataIntegrityViolationException.class);

    ChatMessage pending = message(first, ChatRole.ASSISTANT, ChatMessageStatus.PENDING, null, null);
    messages.saveAndFlush(pending);
    ChatMessage duplicatePending = message(first, ChatRole.ASSISTANT, ChatMessageStatus.PENDING, null, null);
    assertThatThrownBy(() -> messages.saveAndFlush(duplicatePending))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void idempotencyIndexRejectsDuplicateClientMessageInConversation() {
    User user = verifiedUser("chat-repository-message-idempotency");
    Conversation conversation = conversations.saveAndFlush(conversation(user, null));
    UUID clientId = UUID.randomUUID();
    messages.saveAndFlush(message(conversation, ChatRole.USER, ChatMessageStatus.COMPLETED, "one", clientId));

    assertThatThrownBy(() -> messages.saveAndFlush(
        message(conversation, ChatRole.USER, ChatMessageStatus.COMPLETED, "two", clientId)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void cursorQueryReturnsRowsBeforeCreatedAtAndId() {
    User user = verifiedUser("chat-repository-cursor");
    Conversation conversation = conversations.saveAndFlush(conversation(user, null));
    Instant first = Instant.parse("2026-11-16T10:00:00Z");
    Instant second = first.plusSeconds(1);
    ChatMessage older = message(conversation, ChatRole.USER, ChatMessageStatus.COMPLETED, "older", null);
    older.setCreatedAt(first);
    ChatMessage newer = message(conversation, ChatRole.USER, ChatMessageStatus.COMPLETED, "newer", null);
    newer.setCreatedAt(second);
    messages.saveAndFlush(older);
    messages.saveAndFlush(newer);

    var rows = messages.findBefore(conversation.getId(), second, newer.getId(), PageRequest.of(0, 10));

    assertThat(rows).extracting(ChatMessage::getContent).containsExactly("older");
  }

  private Conversation conversation(User user, UUID initialClientId) {
    Conversation conversation = new Conversation();
    conversation.setUser(user);
    conversation.setInitialClientMessageId(initialClientId);
    return conversation;
  }

  private ChatMessage message(Conversation conversation, ChatRole role, ChatMessageStatus status,
      String content, UUID clientId) {
    ChatMessage message = new ChatMessage();
    message.setConversation(conversation);
    message.setRole(role);
    message.setStatus(status);
    message.setContent(content);
    message.setClientMessageId(clientId);
    return message;
  }
}
