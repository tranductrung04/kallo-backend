package com.healthcare.kallo.controller.chat;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.request.chat.ConversationUpdateRequest;
import com.healthcare.kallo.dto.request.chat.SendMessageRequest;
import com.healthcare.kallo.dto.response.chat.ChatReplyResponse;
import com.healthcare.kallo.dto.response.chat.ConversationPageResponse;
import com.healthcare.kallo.dto.response.chat.ConversationResponse;
import com.healthcare.kallo.dto.response.chat.MessagePageResponse;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.config.ChatProperties;
import com.healthcare.kallo.service.chat.ChatResponseStreamHandler;
import com.healthcare.kallo.service.chat.ChatService;
import com.healthcare.kallo.service.chat.ChatTxService;
import com.healthcare.kallo.service.chat.ChatSseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@Tag(name = "Chat", description = "Nutrition chatbot")
public class ChatController {
  private final ChatService chat;
  private final ChatSseService sse;
  private final ChatProperties properties;

  public ChatController(ChatService chat, ChatSseService sse, ChatProperties properties) {
    this.chat = chat;
    this.sse = sse;
    this.properties = properties;
  }

  @PostMapping("/messages")
  @Operation(summary = "Send chat message")
  public ApiResponse<ChatReplyResponse> send(@Valid @RequestBody SendMessageRequest request,
      Authentication authentication) {
    return ApiResponse.success(chat.sendMessage(CurrentUser.id(authentication), request));
  }

  @PostMapping("/messages/{id}/retry")
  @Operation(summary = "Retry failed chat message")
  public ApiResponse<ChatReplyResponse> retry(@PathVariable UUID id, Authentication authentication) {
    return ApiResponse.success(chat.retryMessage(CurrentUser.id(authentication), id));
  }

  @PostMapping(value = "/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @org.springframework.web.bind.annotation.ResponseBody
  @Operation(summary = "Stream chat message response")
  public SseEmitter stream(@Valid @RequestBody SendMessageRequest request, Authentication authentication,
      jakarta.servlet.http.HttpServletResponse response) {
    UUID userId = CurrentUser.id(authentication);
    ChatTxService.SavedMessage saved = chat.prepareStreamMessage(userId, request);
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
    response.setHeader("X-Accel-Buffering", "no");
    SseEmitter emitter = new SseEmitter(properties.sseTimeout().toMillis());
    AtomicBoolean cancelled = new AtomicBoolean();
    emitter.onTimeout(() -> {
      cancelled.set(true);
      emitter.complete();
    });
    emitter.onError(error -> {
      cancelled.set(true);
      emitter.complete();
    });
    ScheduledFuture<?> heartbeat = sse.scheduleHeartbeat(() -> {
      if (cancelled.get()) return;
      try {
        emitter.send(SseEmitter.event().comment("ping"));
      } catch (Exception exception) {
        cancelled.set(true);
        emitter.complete();
      }
    }, properties.sseHeartbeatSeconds());
    emitter.onCompletion(() -> {
      cancelled.set(true);
      heartbeat.cancel(false);
    });
    sse.execute(() -> {
      try {
        emitter.send(SseEmitter.event().comment("connected"));
        chat.streamPreparedMessage(userId, saved, new ChatResponseStreamHandler() {
          @Override
          public void onMeta(UUID conversationId, UUID userMessageId, UUID assistantMessageId) {
            send("meta", java.util.Map.of("conversationId", conversationId, "userMessageId", userMessageId,
                "assistantMessageId", assistantMessageId));
          }

          @Override
          public void onDelta(String text) {
            if (!cancelled.get()) send("delta", java.util.Map.of("text", text));
          }

          @Override
          public void onReplace(String text) {
            send("replace", java.util.Map.of("text", text));
          }

          @Override
          public void onDone(ChatReplyResponseData response) {
            send("done", response);
            emitter.complete();
          }

          private void send(String event, Object data) {
            if (cancelled.get()) throw com.healthcare.kallo.service.chat.ChatSseException
                .clientDisconnected(new IllegalStateException("CLIENT_DISCONNECTED"));
            try {
              emitter.send(SseEmitter.event().name(event).data(data));
            } catch (Exception exception) {
              cancelled.set(true);
              throw new com.healthcare.kallo.service.chat.ChatSseException(exception);
            }
          }
        });
      } catch (Exception exception) {
        if (!cancelled.get()) {
          try {
            String code = exception instanceof com.healthcare.kallo.service.chat.ChatSseException sseException
                ? sseException.code() : "CHAT_STREAM_FAILED";
            emitter.send(SseEmitter.event().name("error").data(java.util.Map.of(
                "code", code, "message", "Chat stream failed")));
          } catch (Exception ignored) {
            // Client already disconnected.
          }
        }
        emitter.complete();
      }
    });
    return emitter;
  }

  @GetMapping("/conversations")
  @Operation(summary = "List user conversations")
  public ApiResponse<ConversationPageResponse> listConversations(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
      Authentication authentication) {
    return ApiResponse.success(chat.listConversations(CurrentUser.id(authentication), page, size));
  }

  @PostMapping("/conversations")
  @Operation(summary = "Create empty conversation")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<ConversationResponse> createConversation(Authentication authentication) {
    return ApiResponse.success(chat.createConversation(CurrentUser.id(authentication)));
  }

  @GetMapping("/conversations/{id}")
  @Operation(summary = "Get conversation")
  public ApiResponse<ConversationResponse> getConversation(@PathVariable UUID id,
      Authentication authentication) {
    return ApiResponse.success(chat.getConversation(CurrentUser.id(authentication), id));
  }

  @PatchMapping("/conversations/{id}")
  @Operation(summary = "Rename conversation")
  public ApiResponse<ConversationResponse> renameConversation(@PathVariable UUID id,
      @Valid @RequestBody ConversationUpdateRequest request, Authentication authentication) {
    return ApiResponse.success(chat.renameConversation(CurrentUser.id(authentication), id, request));
  }

  @DeleteMapping("/conversations/{id}")
  @Operation(summary = "Delete conversation")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteConversation(@PathVariable UUID id, Authentication authentication) {
    chat.deleteConversation(CurrentUser.id(authentication), id);
  }

  @GetMapping("/conversations/{id}/messages")
  @Operation(summary = "List conversation messages")
  public ApiResponse<MessagePageResponse> listMessages(@PathVariable UUID id,
      @RequestParam(required = false) String before, @RequestParam(defaultValue = "30") int limit,
      Authentication authentication) {
    return ApiResponse.success(chat.listMessages(CurrentUser.id(authentication), id, before, limit));
  }
}
