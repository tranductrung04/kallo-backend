package com.healthcare.kallo.service.chat;

public interface ChatAiClient {
  ChatAiReply generate(ChatAiRequest request);
}
