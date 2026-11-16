package com.healthcare.kallo.service.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.healthcare.kallo.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.kallo.config.ChatProperties;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(name = "app.chat.ai.mock-enabled", havingValue = "false", matchIfMissing = true)
public class HttpChatAiClient implements ChatAiClient {
  private final RestClient client;
  private final ChatProperties properties;
  private final ObjectMapper objectMapper;

  public HttpChatAiClient(ChatProperties properties, ObjectMapper objectMapper) {
    this(RestClient.builder()
        .baseUrl(properties.ai().baseUrl())
        .requestFactory(HttpChatAiClient.requestFactory(properties.ai().connectTimeout(), properties.ai().readTimeout()))
        .build(), properties, objectMapper);
  }

  HttpChatAiClient(RestClient client, ChatProperties properties, ObjectMapper objectMapper) {
    this.client = client;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  @Override
  public void stream(ChatAiRequest request, ChatAiStreamHandler handler) {
    client.post()
        .uri("/v1/chat")
        .header("X-Request-Id", UUID.randomUUID().toString())
        .headers(headers -> {
          headers.set(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE);
          String apiKey = properties.ai().apiKey();
          if (apiKey != null && !apiKey.isBlank()) headers.set("X-Internal-Api-Key", apiKey);
        })
        .body(request.streaming())
        .exchange((requestMessage, response) -> {
          if (!response.getStatusCode().is2xxSuccessful()) {
            throw new ChatAiException(ErrorCode.CHAT_AI_UNAVAILABLE);
          }
          readStream(response.getBody(), handler);
          return null;
        });
  }

  @Override
  public ChatAiReply generate(ChatAiRequest request) {
    RuntimeException last = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        return invoke(request);
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
    throw mapAccessFailure((ResourceAccessException) last);
  }

  private Void readStream(java.io.InputStream input, ChatAiStreamHandler handler) {
    ChatAiStreamResult result = new ChatAiStreamResult(List.of(), null, null, null);
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      String event = null;
      String data;
      StringBuilder payload = new StringBuilder();
      while ((data = reader.readLine()) != null) {
        if (data.isEmpty()) {
          if (event != null && payload.length() > 0) {
            result = handleEvent(event, payload.toString(), handler, result);
          }
          event = null;
          payload.setLength(0);
        } else if (data.startsWith("event:")) {
          event = data.substring(6).trim();
        } else if (data.startsWith("data:")) {
          if (payload.length() > 0) payload.append('\n');
          payload.append(data.substring(5).trim());
        }
      }
      if (event != null && payload.length() > 0) handleEvent(event, payload.toString(), handler, result);
      return null;
    } catch (IOException exception) {
      throw new ChatSseException(exception);
    }
  }

  private ChatAiStreamResult handleEvent(String event, String payload, ChatAiStreamHandler handler,
      ChatAiStreamResult current) {
    try {
      JsonNode node = objectMapper.readTree(payload);
      if ("delta".equals(event)) {
        handler.onDelta(node.path("text").asText(""));
        return current;
      }
      if ("done".equals(event)) {
        JsonNode usage = node.path("usage");
        ChatAiStreamResult result = new ChatAiStreamResult(List.of(),
            usage.path("promptTokens").isNumber() ? usage.path("promptTokens").intValue() : null,
            usage.path("completionTokens").isNumber() ? usage.path("completionTokens").intValue() : null,
            node.path("modelVersion").isTextual() ? node.path("modelVersion").textValue() : null);
        handler.onComplete(result);
        return result;
      }
      if ("error".equals(event)) throw new ChatSseException(new IllegalStateException("AI stream error"));
      return current;
    } catch (IOException exception) {
      throw new ChatSseException(exception);
    }
  }

  private ChatAiReply invoke(ChatAiRequest request) {
    AiResponse response = client.post()
        .uri("/v1/chat")
        .header("X-Request-Id", UUID.randomUUID().toString())
        .headers(headers -> {
          String apiKey = properties.ai().apiKey();
          if (apiKey != null && !apiKey.isBlank()) headers.set("X-Internal-Api-Key", apiKey);
        })
        .body(request)
        .retrieve()
        .body(AiResponse.class);
    if (response == null || response.message() == null
        || !"assistant".equalsIgnoreCase(response.message().role())
        || response.message().content() == null || response.message().content().isBlank()) {
      throw new ChatAiException(ErrorCode.CHAT_AI_INVALID_RESPONSE);
    }
    Usage usage = response.usage();
    return new ChatAiReply(response.message().content(), response.suggestedFoods(),
        usage == null ? null : usage.promptTokens(), usage == null ? null : usage.completionTokens(),
        response.modelVersion());
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

  private static SimpleClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeout);
    factory.setReadTimeout(readTimeout);
    return factory;
  }

  private record AiResponse(AiMessage message, List<ChatAiReply.SuggestedFood> suggestedFoods,
      Usage usage, String modelVersion) {}

  private record AiMessage(String role, String content) {}

  private record Usage(@JsonProperty("promptTokens") Integer promptTokens,
      @JsonProperty("completionTokens") Integer completionTokens) {}
}
