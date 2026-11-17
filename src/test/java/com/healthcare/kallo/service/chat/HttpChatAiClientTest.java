package com.healthcare.kallo.service.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.kallo.config.ChatProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class HttpChatAiClientTest {
  @Test
  void streamRelaysDeltaAndDone() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://ai");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    RestClient client = builder.build();
    ChatProperties properties = properties();
    HttpChatAiClient ai = new HttpChatAiClient(client, properties, new tools.jackson.databind.ObjectMapper());
    server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("http://ai/v1/chat"))
        .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
            "event: delta\ndata: {\"text\":\"hello\"}\n\nevent: done\ndata: {}\n\n",
            org.springframework.http.MediaType.TEXT_EVENT_STREAM));
    List<String> deltas = new ArrayList<>();

    ai.stream(new ChatAiRequest(java.util.UUID.randomUUID(), List.of(
        new ChatAiRequest.ChatHistoryMessage("user", "hello"))),
        new ChatAiStreamHandler() {
          @Override public void onDelta(String text) { deltas.add(text); }
          @Override public void onComplete(ChatAiStreamResult result) {}
        });

    assertThat(deltas).containsExactly("hello");
  }

  @Test
  void streamFailsWhenDoneEventIsMissing() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://ai");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    HttpChatAiClient ai = new HttpChatAiClient(builder.build(), properties(), new ObjectMapper());
    server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("http://ai/v1/chat"))
        .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
            "event: delta\ndata: {\"text\":\"hello\"}\n\n", org.springframework.http.MediaType.TEXT_EVENT_STREAM));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> ai.stream(
        new ChatAiRequest(java.util.UUID.randomUUID(), List.of(
            new ChatAiRequest.ChatHistoryMessage("user", "hello"))), new ChatAiStreamHandler() {
          @Override public void onDelta(String text) {}
          @Override public void onComplete(ChatAiStreamResult result) {}
        })).isInstanceOf(ChatSseException.class);
  }

  private ChatProperties properties() {
    return new ChatProperties(true,
        new ChatProperties.Ai("http://ai", "key", Duration.ofSeconds(2), Duration.ofSeconds(30), false),
        12, 2000, new ChatProperties.RateLimit(10, 200), Duration.ofMinutes(2), 30,
        Duration.ofSeconds(60), 15);
  }
}
