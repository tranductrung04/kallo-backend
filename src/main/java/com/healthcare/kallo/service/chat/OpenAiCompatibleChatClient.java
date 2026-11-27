package com.healthcare.kallo.service.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.ChatProperties;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class OpenAiCompatibleChatClient implements ChatAiClient {
  private final RestClient client;
  private final ChatProperties properties;
  private final ChatPromptBuilder promptBuilder;
  private final ObjectMapper objectMapper;
  private final String completionsUri;

  @Autowired
  public OpenAiCompatibleChatClient(
      ChatProperties properties, ChatPromptBuilder promptBuilder, ObjectMapper objectMapper) {
    this(
        RestClient.builder()
            .baseUrl(trimTrailingSlash(properties.llm().baseUrl()))
            .requestFactory(requestFactory(properties.llm().connectTimeout(), properties.llm().readTimeout()))
            .build(),
        properties,
        promptBuilder,
        objectMapper);
  }

  OpenAiCompatibleChatClient(
      RestClient client,
      ChatProperties properties,
      ChatPromptBuilder promptBuilder,
      ObjectMapper objectMapper) {
    this.client = client;
    this.properties = properties;
    this.promptBuilder = promptBuilder;
    this.objectMapper = objectMapper;
    this.completionsUri = resolveCompletionsPath(properties.llm().baseUrl());
  }

  @Override
  public ChatAiReply generate(ChatAiRequest request) {
    List<LlmMessage> messages = buildMessages(request);
    LlmRequest payload = new LlmRequest(
        properties.llm().model(),
        messages,
        properties.llm().temperature(),
        properties.llm().maxTokens(),
        false);
    return executeSyncWithRetry(payload);
  }

  @Override
  public void stream(ChatAiRequest request, ChatAiStreamHandler handler) {
    List<LlmMessage> messages = buildMessages(request);
    LlmRequest payload = new LlmRequest(
        properties.llm().model(),
        messages,
        properties.llm().temperature(),
        properties.llm().maxTokens(),
        true);

    Throwable last = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      StreamState state = new StreamState();
      try {
        invokeStream(payload, handler, state);
        return;
      } catch (ResourceAccessException exception) {
        last = exception;
        if (attempt == 0 && !state.emittedAnyDelta && !isTimeout(exception)) continue;
        throw mapAccessFailure(exception);
      } catch (RestClientResponseException exception) {
        last = exception;
        if (attempt == 0 && !state.emittedAnyDelta && exception.getStatusCode().is5xxServerError()) continue;
        throw mapHttpFailure(exception);
      } catch (ChatAiException exception) {
        throw exception;
      } catch (ChatSseException exception) {
        if (isTimeout(exception)) {
          throw new ChatAiException(ErrorCode.CHAT_AI_TIMEOUT);
        }
        throw exception;
      } catch (RestClientException exception) {
        throw new ChatAiException(ErrorCode.CHAT_AI_UNAVAILABLE);
      }
    }
    throw mapAccessFailure(last);
  }

  @Override
  public String summarize(String existingSummary, List<ChatAiRequest.ChatHistoryMessage> olderMessages) {
    if (olderMessages == null || olderMessages.isEmpty()) return existingSummary;
    String summaryPrompt = promptBuilder.buildSummaryPrompt(existingSummary, olderMessages);
    LlmRequest payload = new LlmRequest(
        properties.llm().model(),
        List.of(
            new LlmMessage("system", "Bạn là trợ lý tóm tắt ngữ cảnh hội thoại dinh dưỡng ngắn gọn, chính xác."),
            new LlmMessage("user", summaryPrompt)),
        0.2,
        256,
        false);
    ChatAiReply reply = executeSyncWithRetry(payload);
    return reply.content();
  }

  private ChatAiReply executeSyncWithRetry(LlmRequest payload) {
    Throwable last = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        return invokeSync(payload);
      } catch (ResourceAccessException exception) {
        last = exception;
        if (attempt == 0 && !isTimeout(exception)) continue;
        throw mapAccessFailure(exception);
      } catch (RestClientResponseException exception) {
        last = exception;
        if (attempt == 0 && exception.getStatusCode().is5xxServerError()) continue;
        throw mapHttpFailure(exception);
      } catch (RestClientException exception) {
        throw new ChatAiException(ErrorCode.CHAT_AI_UNAVAILABLE);
      }
    }
    throw mapAccessFailure(last);
  }

  private ChatAiReply invokeSync(LlmRequest payload) {
    LlmResponse response = client.post()
        .uri(completionsUri)
        .headers(this::applyHeaders)
        .contentType(MediaType.APPLICATION_JSON)
        .body(payload)
        .retrieve()
        .body(LlmResponse.class);

    if (response == null || response.choices() == null || response.choices().isEmpty()) {
      throw new ChatAiException(ErrorCode.CHAT_AI_INVALID_RESPONSE);
    }
    LlmChoice first = response.choices().getFirst();
    if (first == null || first.message() == null
        || first.message().content() == null || first.message().content().isBlank()) {
      throw new ChatAiException(ErrorCode.CHAT_AI_INVALID_RESPONSE);
    }
    LlmUsage usage = response.usage();
    String modelVersion = response.model() != null && !response.model().isBlank()
        ? response.model() : properties.llm().model();
    return new ChatAiReply(
        first.message().content().trim(),
        List.of(),
        usage == null ? null : usage.promptTokens(),
        usage == null ? null : usage.completionTokens(),
        modelVersion);
  }

  private void invokeStream(LlmRequest payload, ChatAiStreamHandler handler, StreamState state) {
    client.post()
        .uri(completionsUri)
        .headers(headers -> {
          applyHeaders(headers);
          headers.set(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE);
        })
        .contentType(MediaType.APPLICATION_JSON)
        .body(payload)
        .exchange((req, response) -> {
          if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RestClientResponseException(
                "LLM stream returned " + response.getStatusCode(),
                response.getStatusCode().value(),
                response.getStatusText(),
                null,
                null,
                null);
          }
          readSseStream(response.getBody(), handler, state);
          return null;
        });
  }

  private void readSseStream(InputStream input, ChatAiStreamHandler handler, StreamState state) {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      String line;
      StringBuilder eventData = new StringBuilder();
      while ((line = reader.readLine()) != null) {
        if (line.isEmpty()) {
          if (eventData.length() > 0) {
            boolean done = processStreamPayload(eventData.toString(), handler, state);
            eventData.setLength(0);
            if (done) return;
          }
        } else if (line.startsWith("data:")) {
          if (eventData.length() > 0) eventData.append('\n');
          eventData.append(line.substring(5).trim());
        }
      }
      if (eventData.length() > 0) {
        boolean done = processStreamPayload(eventData.toString(), handler, state);
        if (done) return;
      }
      if (state.receivedFinishReason && state.emittedAnyDelta) {
        completeStream(handler, state);
        return;
      }
      throw new ChatSseException(new IllegalStateException("LLM stream ended before [DONE]"));
    } catch (IOException exception) {
      if (isTimeout(exception)) {
        throw new ChatAiException(ErrorCode.CHAT_AI_TIMEOUT);
      }
      throw new ChatSseException(exception);
    }
  }

  private boolean processStreamPayload(String rawPayload, ChatAiStreamHandler handler, StreamState state) {
    String trimmed = rawPayload.trim();
    if (trimmed.isEmpty()) return false;
    if ("[DONE]".equals(trimmed)) {
      if (!state.emittedAnyDelta) {
        throw new ChatSseException(new IllegalStateException("LLM stream completed with empty content"));
      }
      completeStream(handler, state);
      return true;
    }
    try {
      JsonNode node = objectMapper.readTree(trimmed);
      if (node.has("error") && !node.path("error").isNull()) {
        String message = node.path("error").path("message").asText("LLM stream error");
        throw new ChatSseException("CHAT_AI_UNAVAILABLE", message);
      }
      if (node.path("model").isTextual() && !node.path("model").textValue().isBlank()) {
        state.modelVersion = node.path("model").textValue();
      }
      JsonNode usage = node.path("usage");
      if (usage.isObject()) {
        if (usage.path("prompt_tokens").isNumber()) state.promptTokens = usage.path("prompt_tokens").intValue();
        if (usage.path("completion_tokens").isNumber()) state.completionTokens = usage.path("completion_tokens").intValue();
      }
      JsonNode choices = node.path("choices");
      if (choices.isArray() && !choices.isEmpty()) {
        JsonNode first = choices.get(0);
        if (first.path("finish_reason").isTextual() && !first.path("finish_reason").textValue().isBlank()) {
          state.receivedFinishReason = true;
        }
        JsonNode deltaNode = first.path("delta").path("content");
        if (deltaNode.isTextual()) {
          String delta = deltaNode.textValue();
          if (!delta.isEmpty()) {
            state.emittedAnyDelta = true;
            handler.onDelta(delta);
          }
        }
      }
      return false;
    } catch (JacksonException exception) {
      throw new ChatSseException(exception);
    }
  }

  private void completeStream(ChatAiStreamHandler handler, StreamState state) {
    String model = state.modelVersion != null ? state.modelVersion : properties.llm().model();
    handler.onComplete(new ChatAiStreamResult(List.of(), state.promptTokens, state.completionTokens, model));
  }

  private List<LlmMessage> buildMessages(ChatAiRequest request) {
    List<LlmMessage> list = new ArrayList<>();
    list.add(new LlmMessage("system", promptBuilder.buildSystemPrompt(request)));
    for (ChatAiRequest.ChatHistoryMessage message : request.messages()) {
      list.add(new LlmMessage(message.role(), message.content()));
    }
    return list;
  }

  private void applyHeaders(HttpHeaders headers) {
    String apiKey = properties.llm().apiKey();
    if (apiKey != null && !apiKey.isBlank()) {
      headers.setBearerAuth(apiKey.trim());
    }
    String requestId = MDC.get("request_id");
    headers.set("X-Request-Id", (requestId == null || requestId.isBlank()) ? UUID.randomUUID().toString() : requestId);
  }

  private RuntimeException mapHttpFailure(RestClientResponseException exception) {
    int status = exception.getStatusCode().value();
    if (status == 408) return new ChatAiException(ErrorCode.CHAT_AI_TIMEOUT);
    if (status == 401 || status == 403 || status == 429 || status >= 500) {
      return new ChatAiException(ErrorCode.CHAT_AI_UNAVAILABLE);
    }
    return new ChatAiException(ErrorCode.CHAT_AI_INVALID_RESPONSE);
  }

  private RuntimeException mapAccessFailure(Throwable exception) {
    return new ChatAiException(isTimeout(exception) ? ErrorCode.CHAT_AI_TIMEOUT : ErrorCode.CHAT_AI_UNAVAILABLE);
  }

  private boolean isTimeout(Throwable error) {
    for (Throwable current = error; current != null; current = current.getCause()) {
      if (current instanceof SocketTimeoutException || current instanceof HttpTimeoutException
          || current instanceof TimeoutException) return true;
    }
    return false;
  }

  private static String trimTrailingSlash(String url) {
    String trimmed = url.trim();
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }

  static String resolveCompletionsPath(String baseUrl) {
    String clean = trimTrailingSlash(baseUrl);
    if (clean.endsWith("/chat/completions")) return "";
    URI uri = URI.create(clean);
    String path = uri.getPath();
    if (path == null || path.isEmpty() || "/".equals(path)) {
      return "/v1/chat/completions";
    }
    return "/chat/completions";
  }

  private static SimpleClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeout);
    factory.setReadTimeout(readTimeout);
    return factory;
  }

  private static final class StreamState {
    boolean emittedAnyDelta;
    boolean receivedFinishReason;
    Integer promptTokens;
    Integer completionTokens;
    String modelVersion;
  }

  private record LlmRequest(
      String model,
      List<LlmMessage> messages,
      double temperature,
      @JsonProperty("max_tokens") int maxTokens,
      boolean stream) {}

  private record LlmMessage(String role, String content) {}

  private record LlmResponse(String model, List<LlmChoice> choices, LlmUsage usage) {}

  private record LlmChoice(LlmMessage message) {}

  private record LlmUsage(
      @JsonProperty("prompt_tokens") Integer promptTokens,
      @JsonProperty("completion_tokens") Integer completionTokens) {}
}
