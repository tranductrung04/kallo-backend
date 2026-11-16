package com.healthcare.kallo.service.chat;

import com.healthcare.kallo.common.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.chat.ai.mock-enabled", havingValue = "false", matchIfMissing = true)
public class UnavailableChatAiClient implements ChatAiClient {
  @Override
  public ChatAiReply generate(ChatAiRequest request) {
    throw new ChatAiException(ErrorCode.CHAT_AI_UNAVAILABLE);
  }
}
