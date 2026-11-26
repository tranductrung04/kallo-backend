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
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {
  private static final Logger log = LoggerFactory.getLogger(ChatService.class);
  private static final int MAX_STREAMED_OUTPUT_LENGTH = 20_000;
  private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?:\\n|[.!?]\\s+)");

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
  private final ObjectProvider<ChatSummaryService> summaryService;

  public ChatService(ConversationRepository conversations, ChatMessageRepository messages, ChatAiClient aiClient,
      ChatContextBuilder contextBuilder, ChatSafetyService safety, ChatRateLimiter rateLimiter,
      ChatProperties properties, ChatTxService tx, ChatMetrics metrics, NotificationService notifications) {
    this(conversations, messages, aiClient, contextBuilder, safety, rateLimiter, properties, tx, metrics,
        notifications, null);
  }

  @Autowired
  public ChatService(ConversationRepository conversations, ChatMessageRepository messages, ChatAiClient aiClient,
      ChatContextBuilder contextBuilder, ChatSafetyService safety, ChatRateLimiter rateLimiter,
      ChatProperties properties, ChatTxService tx, ChatMetrics metrics, NotificationService notifications,
      ObjectProvider<ChatSummaryService> summaryService) {
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
    this.summaryService = summaryService;
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

    ChatSafetyService.InputPolicyDecision policy = safety.checkInputPolicy(saved.userMessage().getContent());
    if (policy.blocked()) {
      ChatAiReply safeReply = new ChatAiReply(
          policy.safeResponse(), List.of(), 0, 0, "kallo-guardrail", policy.reason(), true, null, null);
      if (!tx.completeAssistant(saved.assistantMessage(), policy.safeResponse(), safeReply, 0L, false)) {
        throw new ChatAiException(ErrorCode.CHAT_CONVERSATION_BUSY, saved.userMessage().getId());
      }
      metrics.recordMessage("completed");
      return reply(saved.userMessage(), saved.assistantMessage());
    }

    ChatAiReply generated;
    String checked;
    boolean allergenWarning;
    long latencyMs;
    var aiTimer = metrics.startAiTimer();
    try {
      long started = System.nanoTime();
      ChatAiRequest aiRequest = contextBuilder.build(
          userId, saved.conversation().getId(), saved.userMessage(), properties.historyWindow(),
          properties.maxContextChars());
      generated = aiClient.generate(aiRequest);
      boolean hasMedical = !aiRequest.systemContext().chronicConditions().isEmpty() || generated.disclaimerAdded();
      var validation = safety.validateOutputWithGuardrails(
          generated.content(),
          aiRequest.systemContext().allAllergyTerms(),
          generated.suggestedFoods(),
          hasMedical);
      checked = validation.content();
      allergenWarning = validation.replaced() || validation.suggestedFoods().size() != generated.suggestedFoods().size();
      if (allergenWarning) {
        metrics.recordAllergenHit();
      }
      if (allergenWarning || validation.disclaimerAdded()) {
        generated = generated.withContentAndFoods(checked, validation.suggestedFoods(), validation.disclaimerAdded());
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
      log.error("Chat LLM call failed conversationId={} messageId={}", saved.conversation().getId(),
          saved.userMessage().getId(), exception);
      tx.markFailed(saved.assistantMessage(), ErrorCode.CHAT_AI_UNAVAILABLE.name());
      throw new ChatAiException(ErrorCode.CHAT_AI_UNAVAILABLE, saved.userMessage().getId());
    }
    if (!tx.completeAssistant(saved.assistantMessage(), checked, generated, latencyMs, allergenWarning)) {
      metrics.recordAiError("conversation_busy");
      throw new ChatAiException(ErrorCode.CHAT_CONVERSATION_BUSY, saved.userMessage().getId());
    }
    metrics.recordMessage("completed");
    triggerAsyncSummary(saved.conversation().getId());
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

    ChatSafetyService.InputPolicyDecision policy = safety.checkInputPolicy(saved.userMessage().getContent());
    if (policy.blocked()) {
      ChatAiReply safeReply = new ChatAiReply(
          policy.safeResponse(), List.of(), 0, 0, "kallo-guardrail", policy.reason(), true, null, null);
      if (!tx.completeAssistant(saved.assistantMessage(), policy.safeResponse(), safeReply, 0L, false)) {
        throw new ChatAiException(ErrorCode.CHAT_CONVERSATION_BUSY, saved.userMessage().getId());
      }
      metrics.recordMessage("completed");
      handler.onMeta(saved.conversation().getId(), saved.userMessage().getId(), saved.assistantMessage().getId());
      handler.onDelta(policy.safeResponse());
      handler.onDone(new ChatResponseStreamHandler.ChatReplyResponseData(saved.conversation().getId(),
          ChatMessageResponse.from(saved.userMessage()), ChatMessageResponse.from(saved.assistantMessage())));
      return;
    }

    ChatTxService.SavedMessage active = saved;
    StringBuilder contentBuilder = new StringBuilder();
    StringBuilder streamedBuilder = new StringBuilder();
    StringBuilder sentenceBuffer = new StringBuilder();
    long started = System.nanoTime();
    var aiTimer = metrics.startAiTimer();
    AtomicBoolean latencyRecorded = new AtomicBoolean();
    AtomicBoolean heldBack = new AtomicBoolean();
    try {
      handler.onMeta(saved.conversation().getId(), saved.userMessage().getId(), saved.assistantMessage().getId());
      ChatAiRequest aiRequest = contextBuilder.build(
          userId, active.conversation().getId(), active.userMessage(), properties.historyWindow(),
          properties.maxContextChars()).streaming();
      List<String> allAllergies = aiRequest.systemContext().allAllergyTerms();
      boolean hasMedicalContext = !aiRequest.systemContext().chronicConditions().isEmpty();

      aiClient.stream(aiRequest, new ChatAiStreamHandler() {
        @Override
        public void onDelta(String text) {
          if (text == null || text.isEmpty()) return;
          if (contentBuilder.codePointCount(0, contentBuilder.length()) + text.codePointCount(0, text.length())
              > MAX_STREAMED_OUTPUT_LENGTH) {
            throw new ChatSseException("CHAT_AI_OUTPUT_TOO_LARGE", "AI output exceeds maximum length");
          }
          contentBuilder.append(text);
          if (heldBack.get()) return;
          if (allAllergies.isEmpty()) {
            streamedBuilder.append(text);
            handler.onDelta(text);
            return;
          }
          sentenceBuffer.append(text);
          if (SENTENCE_BOUNDARY.matcher(sentenceBuffer).find()) {
            String segment = sentenceBuffer.toString();
            sentenceBuffer.setLength(0);
            if (safety.containsAnyAllergen(segment, allAllergies)) {
              heldBack.set(true);
            } else {
              streamedBuilder.append(segment);
              handler.onDelta(segment);
            }
          }
        }

        @Override
        public void onComplete(ChatAiStreamResult result) {
          if (!heldBack.get() && sentenceBuffer.length() > 0) {
            String tail = sentenceBuffer.toString();
            sentenceBuffer.setLength(0);
            if (safety.containsAnyAllergen(tail, allAllergies)) {
              heldBack.set(true);
            } else {
              streamedBuilder.append(tail);
              handler.onDelta(tail);
            }
          }
          String raw = contentBuilder.toString();
          var validation = safety.validateOutputWithGuardrails(
              raw,
              allAllergies,
              result.suggestedFoods(),
              hasMedicalContext || result.disclaimerAdded());
          String checked = validation.content();
          boolean allergenWarning = validation.replaced()
              || validation.suggestedFoods().size() != result.suggestedFoods().size();
          ChatAiReply reply = new ChatAiReply(
              checked,
              validation.suggestedFoods(),
              result.promptTokens(),
              result.completionTokens(),
              result.modelVersion(),
              result.intent(),
              result.disclaimerAdded() || validation.disclaimerAdded(),
              result.updatedSummary(),
              result.suggestedTitle());
          if (allergenWarning) {
            metrics.recordAllergenHit();
          }
          if (!tx.completeAssistant(active.assistantMessage(), checked, reply,
              TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), allergenWarning)) {
            metrics.recordAiError("conversation_busy");
            throw new ChatAiException(ErrorCode.CHAT_CONVERSATION_BUSY, active.userMessage().getId());
          }
          if (latencyRecorded.compareAndSet(false, true)) metrics.recordAiLatency(aiTimer);
          metrics.recordMessage("completed");
          triggerAsyncSummary(active.conversation().getId());

          String alreadyStreamed = streamedBuilder.toString();
          if (validation.replaced() || heldBack.get()) {
            handler.onReplace(checked);
          } else if (checked.length() > alreadyStreamed.length() && checked.startsWith(alreadyStreamed)) {
            handler.onDelta(checked.substring(alreadyStreamed.length()));
          } else if (!checked.equals(alreadyStreamed)) {
            handler.onReplace(checked);
          }
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

  private void triggerAsyncSummary(UUID conversationId) {
    ChatSummaryService service = summaryService == null ? null : summaryService.getIfAvailable();
    if (service != null) {
      service.summarizeIfNeeded(conversationId, properties.historyWindow());
    }
  }

  @Transactional(readOnly = true)
  public ConversationPageResponse listConversations(UUID userId, int page, int size) {
    if (!properties.enabled()) throw new BusinessException(ErrorCode.NOT_FOUND);
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
    if (!properties.enabled()) throw new BusinessException(ErrorCode.NOT_FOUND);
    return ConversationResponse.from(conversation(userId, conversationId));
  }

  @Transactional
  public ConversationResponse renameConversation(UUID userId, UUID conversationId, ConversationUpdateRequest request) {
    if (!properties.enabled()) throw new BusinessException(ErrorCode.NOT_FOUND);
    Conversation conversation = conversation(userId, conversationId);
    conversation.setTitle(safety.validateAndNormalizeTitle(request.title()));
    return ConversationResponse.from(conversation);
  }

  @Transactional
  public void deleteConversation(UUID userId, UUID conversationId) {
    if (!properties.enabled()) throw new BusinessException(ErrorCode.NOT_FOUND);
    Conversation conversation = conversation(userId, conversationId);
    conversation.setDeletedAt(Instant.now());
    conversation.setInitialClientMessageId(null);
  }

  @Transactional(readOnly = true)
  public MessagePageResponse listMessages(UUID userId, UUID conversationId, String before, int limit) {
    if (!properties.enabled()) throw new BusinessException(ErrorCode.NOT_FOUND);
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
