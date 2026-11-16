package com.healthcare.kallo.service.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.ChatProperties;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

  public HttpChatAiClient(ChatProperties properties) {
    this(RestClient.builder()
        .baseUrl(properties.ai().baseUrl())
        .requestFactory(HttpChatAiClient.requestFactory(properties.ai().connectTimeout(), properties.ai().readTimeout()))
        .build(), properties);
  }

  HttpChatAiClient(RestClient client, ChatProperties properties) {
    this.client = client;
    this.properties = properties;
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
