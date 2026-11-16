package com.healthcare.kallo.service.chat;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.ChatProperties;
import com.healthcare.kallo.dto.request.chat.ConversationUpdateRequest;
import com.healthcare.kallo.dto.request.chat.SendMessageRequest;
import com.healthcare.kallo.dto.response.chat.ChatMessageResponse;
import com.healthcare.kallo.dto.response.chat.ChatReplyResponse;
import com.healthcare.kallo.dto.response.chat.ConversationPageResponse;
import com.healthcare.kallo.dto.response.chat.ConversationResponse;
import com.healthcare.kallo.dto.response.chat.MessagePageResponse;
import com.healthcare.kallo.model.chat.ChatMessage;
import com.healthcare.kallo.model.chat.Conversation;
import com.healthcare.kallo.repository.chat.ChatMessageRepository;
import com.healthcare.kallo.repository.chat.ConversationRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {
  private static final Logger log = LoggerFactory.getLogger(ChatService.class);
  private final ConversationRepository conversations;
  private final ChatMessageRepository messages;
  private final ChatAiClient aiClient;
  private final ChatContextBuilder contextBuilder;
  private final ChatSafetyService safety;
  private final ChatRateLimiter rateLimiter;
  private final ChatProperties properties;
  private final ChatTxService tx;

  public ChatService(ConversationRepository conversations, ChatMessageRepository messages, ChatAiClient aiClient,
      ChatContextBuilder contextBuilder, ChatSafetyService safety, ChatRateLimiter rateLimiter,
      ChatProperties properties, ChatTxService tx) {
    this.conversations = conversations;
    this.messages = messages;
    this.aiClient = aiClient;
    this.contextBuilder = contextBuilder;
    this.safety = safety;
    this.rateLimiter = rateLimiter;
    this.properties = properties;
    this.tx = tx;
  }

  public ChatReplyResponse sendMessage(UUID userId, SendMessageRequest request) {
    if (!properties.enabled()) throw new BusinessException(ErrorCode.NOT_FOUND);
    String content = safety.validateAndNormalizeInput(request.content(), properties.maxMessageLength());
    ChatTxService.SavedMessage saved;
    try {
      saved = tx.findExistingMessage(userId, request);
      if (saved != null) {
        if (saved.alreadyAnswered()) return reply(saved.userMessage(), saved.assistantMessage());
        rateLimiter.check(userId);
        saved = tx.retryFailed(saved);
      } else {
        rateLimiter.check(userId);
        saved = tx.saveUserMessage(userId, request, content);
      }
    } catch (DataIntegrityViolationException exception) {
      if (tx.isIdempotencyViolation(exception)) {
        saved = tx.retryExistingMessage(userId, request);
      } else {
        throw tx.mapIntegrityViolation(exception);
      }
    }
    if (saved.alreadyAnswered()) return reply(saved.userMessage(), saved.assistantMessage());
    ChatAiReply generated;
    String checked;
    long latencyMs;
    try {
      long started = System.nanoTime();
      generated = aiClient.generate(contextBuilder.build(userId, saved.conversation().getId(), saved.userMessage(),
          properties.historyWindow()));
      checked = safety.validateOutput(generated.content());
      latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    } catch (BusinessException exception) {
      tx.markFailed(saved.assistantMessage(), exception.getErrorCode().name());
      throw exception;
    } catch (RuntimeException exception) {
      log.error("Chat AI call failed conversationId={} messageId={}", saved.conversation().getId(),
          saved.userMessage().getId(), exception);
      tx.markFailed(saved.assistantMessage(), ErrorCode.CHAT_AI_UNAVAILABLE.name());
      throw new ChatAiException(ErrorCode.CHAT_AI_UNAVAILABLE, saved.userMessage().getId());
    }
    if (!tx.completeAssistant(saved.assistantMessage(), checked, generated, latencyMs)) {
      throw new ChatAiException(ErrorCode.CHAT_CONVERSATION_BUSY, saved.userMessage().getId());
    }
    return reply(saved.userMessage(), saved.assistantMessage());
  }

  @Transactional(readOnly = true)
  public ConversationPageResponse listConversations(UUID userId, int page, int size) {
    if (page < 0 || size < 1 || size > 100) throw new BusinessException(ErrorCode.INVALID_PARAMETER);
    var result = conversations.findRecentByUserId(userId, PageRequest.of(page, size));
    return new ConversationPageResponse(result.getContent().stream().map(ConversationResponse::from).toList(),
        page, size, result.getTotalElements(), result.getTotalPages());
  }

  @Transactional
  public ConversationResponse createConversation(UUID userId) {
    if (!properties.enabled()) throw new BusinessException(ErrorCode.NOT_FOUND);
    Conversation conversation = new Conversation();
    conversation.setUser(tx.userReference(userId));
    return ConversationResponse.from(conversations.save(conversation));
  }

  @Transactional(readOnly = true)
  public ConversationResponse getConversation(UUID userId, UUID conversationId) {
    return ConversationResponse.from(conversation(userId, conversationId));
  }

  @Transactional
  public ConversationResponse renameConversation(UUID userId, UUID conversationId, ConversationUpdateRequest request) {
    Conversation conversation = conversation(userId, conversationId);
    conversation.setTitle(safety.validateAndNormalizeTitle(request.title()));
    return ConversationResponse.from(conversation);
  }

  @Transactional
  public void deleteConversation(UUID userId, UUID conversationId) {
    conversation(userId, conversationId).setDeletedAt(Instant.now());
  }

  @Transactional(readOnly = true)
  public MessagePageResponse listMessages(UUID userId, UUID conversationId, String before, int limit) {
    conversation(userId, conversationId);
    if (limit < 1 || limit > 100) throw new BusinessException(ErrorCode.INVALID_PARAMETER);
    List<ChatMessage> result;
    if (before == null || before.isBlank()) {
      result = messages.findByConversationIdOrderByCreatedAtDescIdDesc(conversationId, PageRequest.of(0, limit + 1));
    } else {
      Cursor cursor = decodeCursor(before);
      result = messages.findBefore(conversationId, cursor.createdAt(), cursor.id(), PageRequest.of(0, limit + 1));
    }
    boolean hasMore = result.size() > limit;
    if (hasMore) result = result.subList(0, limit);
    String nextCursor = hasMore ? encodeCursor(result.getLast()) : null;
    return new MessagePageResponse(result.stream().map(ChatMessageResponse::from).toList(), nextCursor, hasMore);
  }

  private Conversation conversation(UUID userId, UUID conversationId) {
    return conversations.findByIdAndUserIdAndDeletedAtIsNull(conversationId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
  }

  private ChatReplyResponse reply(ChatMessage userMessage, ChatMessage assistantMessage) {
    return new ChatReplyResponse(userMessage.getConversation().getId(),
        ChatMessageResponse.from(userMessage), ChatMessageResponse.from(assistantMessage));
  }

  private String encodeCursor(ChatMessage message) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(
        (message.getCreatedAt() + "|" + message.getId()).getBytes(StandardCharsets.UTF_8));
  }

  private Cursor decodeCursor(String value) {
    try {
      String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|", 2);
      return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
    } catch (RuntimeException exception) {
      throw new BusinessException(ErrorCode.INVALID_PARAMETER);
    }
  }

  private record Cursor(Instant createdAt, UUID id) {}
}
