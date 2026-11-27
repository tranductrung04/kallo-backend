package com.healthcare.kallo.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.ChatProperties;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class OpenAiCompatibleChatClientTest {
  private final ChatPromptBuilder promptBuilder = new ChatPromptBuilder();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void resolvesOpenAiCompatiblePathsCorrectly() {
    assertThat(OpenAiCompatibleChatClient.resolveCompletionsPath("http://localhost:11434"))
        .isEqualTo("/v1/chat/completions");
    assertThat(OpenAiCompatibleChatClient.resolveCompletionsPath("http://localhost:11434/v1"))
        .isEqualTo("/chat/completions");
    assertThat(OpenAiCompatibleChatClient.resolveCompletionsPath("https://openrouter.ai/api/v1/"))
        .isEqualTo("/chat/completions");
    assertThat(OpenAiCompatibleChatClient.resolveCompletionsPath("https://generativelanguage.googleapis.com/v1beta/openai"))
        .isEqualTo("/chat/completions");
  }

  @Test
  void generateSendsOpenAiPayloadAndParsesReply() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://api.example.com/v1");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    OpenAiCompatibleChatClient llm = new OpenAiCompatibleChatClient(
        builder.build(), properties("https://api.example.com/v1", "secret-key"), promptBuilder, objectMapper);

    server.expect(requestTo("https://api.example.com/v1/chat/completions"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer secret-key"))
        .andExpect(jsonPath("$.model").value("qwen2.5:7b"))
        .andExpect(jsonPath("$.stream").value(false))
        .andExpect(jsonPath("$.messages[0].role").value("system"))
        .andExpect(jsonPath("$.messages[1].role").value("user"))
        .andRespond(withSuccess(
            """
            {
              "model": "qwen2.5:7b-instruct",
              "choices": [{"index": 0, "message": {"role": "assistant", "content": "Bạn còn 450 kcal hôm nay."}}],
              "usage": {"prompt_tokens": 120, "completion_tokens": 24}
            }
            """,
            MediaType.APPLICATION_JSON));

    ChatAiReply reply = llm.generate(new ChatAiRequest(
        UUID.randomUUID(), List.of(new ChatAiRequest.ChatHistoryMessage("user", "Hôm nay còn bao nhiêu calo?"))));

    server.verify();
    assertThat(reply.content()).isEqualTo("Bạn còn 450 kcal hôm nay.");
    assertThat(reply.promptTokens()).isEqualTo(120);
    assertThat(reply.completionTokens()).isEqualTo(24);
    assertThat(reply.modelVersion()).isEqualTo("qwen2.5:7b-instruct");
  }

  @Test
  void streamParsesOpenAiSseDeltasAndDone() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:11434/v1");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    OpenAiCompatibleChatClient llm = new OpenAiCompatibleChatClient(
        builder.build(), properties("http://localhost:11434/v1", ""), promptBuilder, objectMapper);

    String sseBody = """
        data: {"model":"qwen2.5:7b","choices":[{"delta":{"content":"Xin "}}]}

        data: {"model":"qwen2.5:7b","choices":[{"delta":{"content":"chào!"},"finish_reason":"stop"}],"usage":{"prompt_tokens":50,"completion_tokens":8}}

        data: [DONE]

        """;

    server.expect(requestTo("http://localhost:11434/v1/chat/completions"))
        .andExpect(jsonPath("$.stream").value(true))
        .andRespond(withSuccess(sseBody, MediaType.TEXT_EVENT_STREAM));

    List<String> deltas = new ArrayList<>();
    List<ChatAiStreamResult> completions = new ArrayList<>();
    llm.stream(
        new ChatAiRequest(UUID.randomUUID(), List.of(new ChatAiRequest.ChatHistoryMessage("user", "Chào"))),
        new ChatAiStreamHandler() {
          @Override public void onDelta(String text) { deltas.add(text); }
          @Override public void onComplete(ChatAiStreamResult result) { completions.add(result); }
        });

    server.verify();
    assertThat(deltas).containsExactly("Xin ", "chào!");
    assertThat(completions).hasSize(1);
    assertThat(completions.getFirst().promptTokens()).isEqualTo(50);
    assertThat(completions.getFirst().completionTokens()).isEqualTo(8);
    assertThat(completions.getFirst().modelVersion()).isEqualTo("qwen2.5:7b");
  }

  @Test
  void mapsStreamSocketTimeoutToChatAiTimeout() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://api.example.com/v1");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    OpenAiCompatibleChatClient llm = new OpenAiCompatibleChatClient(
        builder.build(), properties("https://api.example.com/v1", "k"), promptBuilder, objectMapper);

    server.expect(requestTo("https://api.example.com/v1/chat/completions"))
        .andRespond(withException(new SocketTimeoutException("Read timed out")));

    assertThatThrownBy(() -> llm.stream(
        new ChatAiRequest(UUID.randomUUID(), List.of(new ChatAiRequest.ChatHistoryMessage("user", "Hello"))),
        new ChatAiStreamHandler() {
          @Override public void onDelta(String text) {}
          @Override public void onComplete(ChatAiStreamResult result) {}
        }))
        .isInstanceOf(ChatAiException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.CHAT_AI_TIMEOUT);
  }

  @Test
  void mapsUnauthorizedToUnavailable() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://api.example.com/v1");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    OpenAiCompatibleChatClient llm = new OpenAiCompatibleChatClient(
        builder.build(), properties("https://api.example.com/v1", "bad"), promptBuilder, objectMapper);

    server.expect(requestTo("https://api.example.com/v1/chat/completions"))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    assertThatThrownBy(() -> llm.generate(new ChatAiRequest(
        UUID.randomUUID(), List.of(new ChatAiRequest.ChatHistoryMessage("user", "Hello")))))
        .isInstanceOf(ChatAiException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.CHAT_AI_UNAVAILABLE);
  }

  private ChatProperties properties(String baseUrl, String apiKey) {
    return new ChatProperties(
        true,
        new ChatProperties.Llm(baseUrl, apiKey, "qwen2.5:7b", Duration.ofSeconds(5), Duration.ofSeconds(60), 0.4, 1024),
        12,
        4500,
        2000,
        new ChatProperties.RateLimit(10, 200),
        Duration.ofSeconds(120),
        30,
        Duration.ofSeconds(60),
        15);
  }
}
