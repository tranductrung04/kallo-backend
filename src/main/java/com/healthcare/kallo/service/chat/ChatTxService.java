package com.healthcare.kallo.service.chat;

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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatTxService {
  private final ConversationRepository conversations;
  private final ChatMessageRepository messages;
  private final UserRepository users;
  private final ChatProperties properties;

  public ChatTxService(ConversationRepository conversations, ChatMessageRepository messages, UserRepository users,
      ChatProperties properties) {
    this.conversations = conversations;
    this.messages = messages;
    this.users = users;
    this.properties = properties;
  }

  @Transactional(readOnly = true)
  public SavedMessage findExistingMessage(UUID userId, SendMessageRequest request) {
    Conversation conversation;
    if (request.conversationId() == null) {
      conversation = conversations.findByUserIdAndInitialClientMessageIdAndDeletedAtIsNull(
          userId, request.clientMessageId()).orElse(null);
    } else {
      conversation = conversations.findByIdAndUserIdAndDeletedAtIsNull(request.conversationId(), userId)
          .orElse(null);
    }
    if (conversation == null) return null;
    ChatMessage userMessage = messages.findByConversationIdAndClientMessageId(
        conversation.getId(), request.clientMessageId()).orElse(null);
    if (userMessage == null) return null;
    ChatMessage assistant = messages.findByConversationIdAndParentMessageIdAndRole(
        conversation.getId(), userMessage.getId(), ChatRole.ASSISTANT).orElse(null);
    if (assistant == null || assistant.getStatus() == ChatMessageStatus.PENDING) {
      throw new BusinessException(ErrorCode.CHAT_CONVERSATION_BUSY);
    }
    return new SavedMessage(conversation, userMessage, assistant,
        assistant.getStatus() == ChatMessageStatus.COMPLETED);
  }

  @Transactional
  public SavedMessage retryFailed(SavedMessage existing) {
    Instant pendingSince = Instant.now();
    if (messages.retryFailed(existing.assistantMessage().getId(), pendingSince) == 0) {
      throw new BusinessException(ErrorCode.CHAT_CONVERSATION_BUSY);
    }
    existing.assistantMessage().setStatus(ChatMessageStatus.PENDING);
    existing.assistantMessage().setPendingSince(pendingSince);
    existing.assistantMessage().setContent(null);
    existing.assistantMessage().setMetadata(null);
    return new SavedMessage(existing.conversation(), existing.userMessage(), existing.assistantMessage(), false);
  }

  @Transactional
  public SavedMessage saveUserMessage(UUID userId, SendMessageRequest request, String content) {
    Conversation conversation;
    if (request.conversationId() == null) {
      var existing = conversations.findByUserIdAndInitialClientMessageIdAndDeletedAtIsNull(userId, request.clientMessageId());
      if (existing.isPresent()) return existingReply(existing.get(), request.clientMessageId());
      conversation = new Conversation();
      conversation.setUser(userReference(userId));
      conversation.setInitialClientMessageId(request.clientMessageId());
      conversation = conversations.saveAndFlush(conversation);
    } else {
      conversation = conversations.findByIdAndUserIdAndDeletedAtIsNull(request.conversationId(), userId)
          .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
      var existing = messages.findByConversationIdAndClientMessageId(conversation.getId(), request.clientMessageId());
      if (existing.isPresent()) return existingReply(conversation, existing.get());
    }
    var pending = messages.findByConversationIdAndStatus(conversation.getId(), ChatMessageStatus.PENDING);
    if (pending.isPresent()) {
      Instant pendingSince = pending.get().getPendingSince() == null
          ? pending.get().getCreatedAt() : pending.get().getPendingSince();
      Instant cutoff = Instant.now().minus(properties.pendingTimeout());
      if (pendingSince.isAfter(cutoff)) {
        throw new BusinessException(ErrorCode.CHAT_CONVERSATION_BUSY);
      }
      messages.failPending(pending.get().getId(), Map.of("reason", "PENDING_TIMEOUT"));
    }

    ChatMessage userMessage = new ChatMessage();
    userMessage.setConversation(conversation);
    userMessage.setRole(ChatRole.USER);
    userMessage.setStatus(ChatMessageStatus.COMPLETED);
    userMessage.setContent(content);
    userMessage.setClientMessageId(request.clientMessageId());
    userMessage = messages.saveAndFlush(userMessage);

    ChatMessage assistant = new ChatMessage();
    assistant.setConversation(conversation);
    assistant.setRole(ChatRole.ASSISTANT);
    assistant.setStatus(ChatMessageStatus.PENDING);
    assistant.setParentMessage(userMessage);
    assistant = messages.saveAndFlush(assistant);
    conversations.addMessages(conversation.getId(), userMessage.getCreatedAt(), title(content));
    return new SavedMessage(conversation, userMessage, assistant, false);
  }

  @Transactional
  public boolean completeAssistant(ChatMessage assistant, String content, ChatAiReply generated, long latencyMs) {
    return completeAssistant(assistant, content, generated, latencyMs, false);
  }

  @Transactional
  public boolean completeAssistant(
      ChatMessage assistant,
      String content,
      ChatAiReply generated,
      long latencyMs,
      boolean allergenWarning) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("suggestedFoods", generated.suggestedFoods());
    metadata.put("allergenWarning", allergenWarning);
    metadata.put("disclaimerAdded", generated.disclaimerAdded());
    if (generated.intent() != null) {
      metadata.put("intent", generated.intent());
    }

    int updated = messages.completePending(assistant.getId(), content, generated.promptTokens(),
        generated.completionTokens(), generated.modelVersion(),
        Math.toIntExact(Math.min(Integer.MAX_VALUE, latencyMs)),
        metadata);
    if (updated == 0) return false;
    assistant.setContent(content);
    assistant.setStatus(ChatMessageStatus.COMPLETED);
    assistant.setPendingSince(null);
    assistant.setPromptTokens(generated.promptTokens());
    assistant.setCompletionTokens(generated.completionTokens());
    assistant.setModelVersion(generated.modelVersion());
    assistant.setLatencyMs(Math.toIntExact(Math.min(Integer.MAX_VALUE, latencyMs)));
    assistant.setMetadata(metadata);
    conversations.incrementMessageCount(assistant.getConversation().getId());
    conversations.touchLastMessageAt(
        assistant.getConversation().getId(),
        assistant.getCreatedAt());
    return true;
  }

  @Transactional(readOnly = true)
  public SummarySnapshot loadSummarySnapshot(UUID conversationId, int historyWindow, int minBatchSize) {
    Conversation conversation = conversations.findById(conversationId).orElse(null);
    if (conversation == null || conversation.getDeletedAt() != null) return null;

    List<ChatMessage> completed = messages.findCompletedOrdered(conversationId);
    int endExclusive = completed.size() - historyWindow;
    if (endExclusive <= 0) return null;

    UUID watermark = conversation.getSummarizedThroughMessageId();
    int startInclusive = 0;
    if (watermark != null) {
      int watermarkIdx = -1;
      for (int i = 0; i < completed.size(); i++) {
        if (watermark.equals(completed.get(i).getId())) {
          watermarkIdx = i;
          break;
        }
      }
      if (watermarkIdx >= 0) {
        startInclusive = watermarkIdx + 1;
      } else {
        startInclusive = Math.max(0, endExclusive - minBatchSize);
      }
    }

    if (endExclusive - startInclusive < minBatchSize) {
      return null;
    }

    List<ChatMessage> slice = completed.subList(startInclusive, endExclusive);
    List<ChatAiRequest.ChatHistoryMessage> history = slice.stream()
        .map(m -> new ChatAiRequest.ChatHistoryMessage(m.getRole().name().toLowerCase(), m.getContent()))
        .toList();
    return new SummarySnapshot(
        conversation.getSummary(),
        watermark,
        slice.getLast().getId(),
        history);
  }

  @Transactional
  public boolean saveSummaryWithWatermark(
      UUID conversationId, UUID expectedWatermarkId, UUID newWatermarkId, String summary) {
    return conversations.updateSummaryWithWatermark(conversationId, expectedWatermarkId, newWatermarkId, summary) > 0;
  }

  @Transactional(readOnly = true)
  public SavedMessage retryExistingFirstMessage(UUID userId, UUID clientMessageId) {
    Conversation conversation = conversations.findByUserIdAndInitialClientMessageIdAndDeletedAtIsNull(userId, clientMessageId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_AI_UNAVAILABLE));
    ChatMessage userMessage = messages.findByConversationIdAndClientMessageId(conversation.getId(), clientMessageId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_AI_UNAVAILABLE));
    return existingReply(conversation, userMessage);
  }

  @Transactional(readOnly = true)
  public SavedMessage retryExistingMessage(UUID userId, SendMessageRequest request) {
    if (request.conversationId() == null) return retryExistingFirstMessage(userId, request.clientMessageId());
    Conversation conversation = conversations.findByIdAndUserIdAndDeletedAtIsNull(request.conversationId(), userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    ChatMessage userMessage = messages.findByConversationIdAndClientMessageId(conversation.getId(), request.clientMessageId())
        .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_AI_UNAVAILABLE));
    return existingReply(conversation, userMessage);
  }

  @Transactional
  public SavedMessage retryByAssistantId(UUID userId, UUID assistantMessageId) {
    ChatMessage assistant = messages.findById(assistantMessageId)
        .filter(message -> message.getRole() == ChatRole.ASSISTANT)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    Conversation conversation = conversations.findByIdAndUserIdAndDeletedAtIsNull(
        assistant.getConversation().getId(), userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    if (assistant.getStatus() != ChatMessageStatus.FAILED) {
      throw new BusinessException(assistant.getStatus() == ChatMessageStatus.PENDING
          ? ErrorCode.CHAT_CONVERSATION_BUSY : ErrorCode.INVALID_PARAMETER);
    }
    ChatMessage userMessage = assistant.getParentMessage();
    if (userMessage == null) throw new BusinessException(ErrorCode.INVALID_PARAMETER);
    return retryFailed(new SavedMessage(conversation, userMessage, assistant, false));
  }

  public boolean isIdempotencyViolation(DataIntegrityViolationException exception) {
    String constraint = constraintName(exception);
    return "uq_chat_messages_idempotency".equals(constraint)
        || "uq_conversations_initial_idempotency".equals(constraint);
  }

  public BusinessException mapIntegrityViolation(DataIntegrityViolationException exception) {
    if ("uq_chat_messages_pending_assistant".equals(constraintName(exception))) {
      return new BusinessException(ErrorCode.CHAT_CONVERSATION_BUSY);
    }
    return new BusinessException(ErrorCode.INTERNAL_ERROR);
  }

  @Transactional
  public boolean markFailed(ChatMessage assistant, String reason) {
    return messages.failPending(assistant.getId(), Map.of("reason", reason)) > 0;
  }

  @Transactional
  public int failExpiredPendingMessages() {
    return messages.failExpiredPending(Instant.now().minus(properties.pendingTimeout()),
        Map.of("reason", "PENDING_TIMEOUT"));
  }

  private SavedMessage existingReply(Conversation conversation, UUID clientMessageId) {
    var userMessage = messages.findByConversationIdAndClientMessageId(conversation.getId(), clientMessageId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_CONVERSATION_BUSY));
    return existingReply(conversation, userMessage);
  }

  private SavedMessage existingReply(Conversation conversation, ChatMessage userMessage) {
    var assistant = messages.findByConversationIdAndParentMessageIdAndRole(conversation.getId(), userMessage.getId(), ChatRole.ASSISTANT)
        .orElse(null);
    if (assistant == null || assistant.getStatus() == ChatMessageStatus.PENDING)
      throw new BusinessException(ErrorCode.CHAT_CONVERSATION_BUSY);
    return new SavedMessage(conversation, userMessage, assistant,
        assistant.getStatus() == ChatMessageStatus.COMPLETED);
  }

  private String constraintName(Throwable error) {
    for (Throwable current = error; current != null; current = current.getCause()) {
      if (current instanceof ConstraintViolationException violation) return violation.getConstraintName();
    }
    return null;
  }

  public User userReference(UUID userId) {
    if (!users.existsById(userId)) throw new BusinessException(ErrorCode.NOT_FOUND);
    return users.getReferenceById(userId);
  }

  private String title(String content) {
    String clean = content.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s+", " ").trim();
    if (clean.codePointCount(0, clean.length()) <= 80) return clean;
    String clipped = clean.codePoints().limit(80)
        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
        .toString();
    int lastSpace = clipped.lastIndexOf(' ');
    return lastSpace >= 30 ? clipped.substring(0, lastSpace) : clipped;
  }

  public record SavedMessage(Conversation conversation, ChatMessage userMessage,
      ChatMessage assistantMessage, boolean alreadyAnswered) {}

  public record SummarySnapshot(
      String existingSummary,
      UUID expectedWatermarkId,
      UUID newWatermarkId,
      List<ChatAiRequest.ChatHistoryMessage> unsummarizedMessages) {}
}
