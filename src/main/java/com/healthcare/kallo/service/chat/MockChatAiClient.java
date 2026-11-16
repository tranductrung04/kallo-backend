package com.healthcare.kallo.service.chat;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.chat.ai.mock-enabled", havingValue = "true")
public class MockChatAiClient implements ChatAiClient {
  @Override
  public ChatAiReply generate(ChatAiRequest request) {
    return new ChatAiReply("(Mock) Mình đã nhận: " + request.lastUserMessage(), java.util.List.of(), null, null,
        "mock");
  }
}
