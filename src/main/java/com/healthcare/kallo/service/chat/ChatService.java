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
import com.healthcare.kallo.model.sys.enums.NotificationType;
import com.healthcare.kallo.repository.chat.ChatMessageRepository;
import com.healthcare.kallo.repository.chat.ConversationRepository;
import com.healthcare.kallo.service.sys.NotificationService;
import com.healthcare.kallo.service.sys.NotifyCommand;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {
  private static final Logger log = LoggerFactory.getLogger(ChatService.class);
  private static final int MAX_STREAMED_OUTPUT_LENGTH = 20_000;
  private final ConversationRepository conversations;
  private final ChatMessageRepository messages;
  private final ChatAiClient aiClient;
  private final ChatContextBuilder contextBuilder;
  private final ChatSafetyService safety;
  private final ChatRateLimiter rateLimiter;
  private final ChatProperties properties;
  private final ChatTxService tx;
  private final ChatMetrics metrics;
  private final NotificationService notifications;

  public ChatService(ConversationRepository conversations, ChatMessageRepository messages, ChatAiClient aiClient,
      ChatContextBuilder contextBuilder, ChatSafetyService safety, ChatRateLimiter rateLimiter,
      ChatProperties properties, ChatTxService tx, ChatMetrics metrics, NotificationService notifications) {
    this.conversations = conversations;
    this.messages = messages;
    this.aiClient = aiClient;
    this.contextBuilder = contextBuilder;
    this.safety = safety;
    this.rateLimiter = rateLimiter;
    this.properties = properties;
    this.tx = tx;
    this.metrics = metrics;
    this.notifications = notifications;
  }

  public ChatReplyResponse retryMessage(UUID userId, UUID assistantMessageId) {
    if (!properties.enabled()) throw new BusinessException(ErrorCode.NOT_FOUND);
    ChatTxService.SavedMessage saved = tx.retryByAssistantId(userId, assistantMessageId);
    try {
      rateLimiter.check(userId);
    } catch (BusinessException exception) {
      tx.markFailed(saved.assistantMessage(), exception.getErrorCode().name());
      throw exception;
    }
    return generateReply(userId, saved);
  }

  public void markStreamFailed(ChatTxService.SavedMessage saved, String reason) {
    tx.markFailed(saved.assistantMessage(), reason);
  }

  private ChatReplyResponse generateReply(UUID userId, ChatTxService.SavedMessage saved) {
    if (saved.alreadyAnswered()) return reply(saved.userMessage(), saved.assistantMessage());
    ChatAiReply generated;
    String checked;
    long latencyMs;
    var aiTimer = metrics.startAiTimer();
    try {
      long started = System.nanoTime();
      ChatAiRequest aiRequest = contextBuilder.build(userId, saved.conversation().getId(), saved.userMessage(),
          properties.historyWindow());
      generated = aiClient.generate(aiRequest);
      var validation = safety.validateOutputWithGuardrails(generated.content(),
          aiRequest.systemContext().allergies(), generated.suggestedFoods());
      checked = validation.content();
      if (validation.replaced() || validation.suggestedFoods().size() != generated.suggestedFoods().size()) {
        metrics.recordAllergenHit();
        generated = new ChatAiReply(checked, validation.suggestedFoods(), generated.promptTokens(),
            generated.completionTokens(), generated.modelVersion());
      }
      latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
      metrics.recordAiLatency(aiTimer);
    } catch (BusinessException exception) {
      metrics.recordAiLatency(aiTimer);
      metrics.recordAiError(exception.getErrorCode().name().toLowerCase());
      metrics.recordMessage("failed");
      tx.markFailed(saved.assistantMessage(), exception.getErrorCode().name());
      throw exception;
    } catch (RuntimeException exception) {
      metrics.recordAiLatency(aiTimer);
      metrics.recordAiError("unavailable");
      metrics.recordMessage("failed");
      log.error("Chat AI call failed conversationId={} messageId={}", saved.conversation().getId(),
          saved.userMessage().getId(), exception);
      tx.markFailed(saved.assistantMessage(), ErrorCode.CHAT_AI_UNAVAILABLE.name());
      throw new ChatAiException(ErrorCode.CHAT_AI_UNAVAILABLE, saved.userMessage().getId());
    }
    if (!tx.completeAssistant(saved.assistantMessage(), checked, generated, latencyMs)) {
      metrics.recordAiError("conversation_busy");
      throw new ChatAiException(ErrorCode.CHAT_CONVERSATION_BUSY, saved.userMessage().getId());
    }
    metrics.recordMessage("completed");
    return reply(saved.userMessage(), saved.assistantMessage());
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
        if (!saved.alreadyAnswered()) saved = tx.retryFailed(saved);
      } else {
        throw tx.mapIntegrityViolation(exception);
      }
    }
    return generateReply(userId, saved);
  }

  public ChatTxService.SavedMessage prepareStreamMessage(UUID userId, SendMessageRequest request) {
    if (!properties.enabled()) throw new BusinessException(ErrorCode.NOT_FOUND);
    String content = safety.validateAndNormalizeInput(request.content(), properties.maxMessageLength());
    try {
      ChatTxService.SavedMessage saved = tx.findExistingMessage(userId, request);
      if (saved != null) {
        if (saved.alreadyAnswered()) return saved;
        rateLimiter.check(userId);
        return tx.retryFailed(saved);
      }
      rateLimiter.check(userId);
      return tx.saveUserMessage(userId, request, content);
    } catch (DataIntegrityViolationException exception) {
      if (tx.isIdempotencyViolation(exception)) {
        ChatTxService.SavedMessage saved = tx.retryExistingMessage(userId, request);
        return saved.alreadyAnswered() ? saved : tx.retryFailed(saved);
      }
      throw tx.mapIntegrityViolation(exception);
    }
  }

  public void streamMessage(UUID userId, SendMessageRequest request, ChatResponseStreamHandler handler) {
    streamPreparedMessage(userId, prepareStreamMessage(userId, request), handler);
  }

  public void streamPreparedMessage(UUID userId, ChatTxService.SavedMessage saved,
      ChatResponseStreamHandler handler) {
    if (saved.alreadyAnswered()) {
      handler.onMeta(saved.conversation().getId(), saved.userMessage().getId(), saved.assistantMessage().getId());
      handler.onDelta(saved.assistantMessage().getContent());
      handler.onDone(new ChatResponseStreamHandler.ChatReplyResponseData(saved.conversation().getId(),
          ChatMessageResponse.from(saved.userMessage()), ChatMessageResponse.from(saved.assistantMessage())));
      return;
    }
    ChatTxService.SavedMessage active = saved;
    StringBuilder contentBuilder = new StringBuilder();
    long started = System.nanoTime();
    var aiTimer = metrics.startAiTimer();
    AtomicBoolean latencyRecorded = new AtomicBoolean();
    try {
      handler.onMeta(saved.conversation().getId(), saved.userMessage().getId(), saved.assistantMessage().getId());
      ChatAiRequest aiRequest = contextBuilder.build(userId, active.conversation().getId(), active.userMessage(),
          properties.historyWindow()).streaming();
      aiClient.stream(aiRequest, new ChatAiStreamHandler() {
        @Override
        public void onDelta(String text) {
          if (text == null || text.isEmpty()) return;
          if (contentBuilder.codePointCount(0, contentBuilder.length()) + text.codePointCount(0, text.length())
              > MAX_STREAMED_OUTPUT_LENGTH) {
            throw new ChatSseException("CHAT_AI_OUTPUT_TOO_LARGE", "AI output exceeds maximum length");
          }
          contentBuilder.append(text);
          if (aiRequest.systemContext().allergies().isEmpty()) handler.onDelta(text);
        }

        @Override
        public void onComplete(ChatAiStreamResult result) {
          var validation = safety.validateOutputWithGuardrails(contentBuilder.toString(),
              aiRequest.systemContext().allergies(), result.suggestedFoods());
          String checked = validation.content();
          ChatAiReply reply = new ChatAiReply(checked, validation.suggestedFoods(), result.promptTokens(),
              result.completionTokens(), result.modelVersion());
          if (validation.replaced() || validation.suggestedFoods().size() != result.suggestedFoods().size()) {
            metrics.recordAllergenHit();
          }
          if (!tx.completeAssistant(active.assistantMessage(), checked, reply,
              TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))) {
            metrics.recordAiError("conversation_busy");
            throw new ChatAiException(ErrorCode.CHAT_CONVERSATION_BUSY, active.userMessage().getId());
          }
          if (latencyRecorded.compareAndSet(false, true)) metrics.recordAiLatency(aiTimer);
          metrics.recordMessage("completed");
          if (!aiRequest.systemContext().allergies().isEmpty() || validation.replaced()) handler.onReplace(checked);
          handler.onDone(new ChatResponseStreamHandler.ChatReplyResponseData(active.conversation().getId(),
              ChatMessageResponse.from(active.userMessage()), ChatMessageResponse.from(active.assistantMessage())));
          if (handler.clientDisconnected()) notifyReply(userId, active);
        }
      });
    } catch (BusinessException exception) {
      if (latencyRecorded.compareAndSet(false, true)) metrics.recordAiLatency(aiTimer);
      metrics.recordMessage("failed");
      metrics.recordAiError(exception.getErrorCode().name().toLowerCase());
      tx.markFailed(active.assistantMessage(), exception.getErrorCode().name());
      throw exception;
    } catch (RuntimeException exception) {
      if (latencyRecorded.compareAndSet(false, true)) metrics.recordAiLatency(aiTimer);
      if (exception instanceof ChatSseException sseException) {
        if (sseException.clientDisconnected()) {
          tx.markFailed(active.assistantMessage(), sseException.code());
          return;
        }
        metrics.recordMessage("failed");
        metrics.recordAiError(sseException.code().toLowerCase());
        tx.markFailed(active.assistantMessage(), sseException.code());
        throw exception;
      }
      metrics.recordMessage("failed");
      metrics.recordAiError("unavailable");
      tx.markFailed(active.assistantMessage(), ErrorCode.CHAT_AI_UNAVAILABLE.name());
      throw new ChatAiException(ErrorCode.CHAT_AI_UNAVAILABLE, active.userMessage().getId());
    }
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
    Conversation conversation = conversation(userId, conversationId);
    conversation.setDeletedAt(Instant.now());
    conversation.setInitialClientMessageId(null);
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

  private void notifyReply(UUID userId, ChatTxService.SavedMessage saved) {
    try {
      notifications.notify(new NotifyCommand(userId, NotificationType.CHAT_REPLY,
          Map.of(), Map.of("conversationId", saved.conversation().getId().toString(),
              "messageId", saved.assistantMessage().getId().toString()),
          "chat-reply:" + saved.assistantMessage().getId()));
    } catch (RuntimeException exception) {
      log.warn("Unable to publish chat reply notification conversationId={} messageId={}",
          saved.conversation().getId(), saved.assistantMessage().getId(), exception);
    }
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
