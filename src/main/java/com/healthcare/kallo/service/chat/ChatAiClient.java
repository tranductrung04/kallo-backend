package com.healthcare.kallo.service.chat;

public interface ChatAiClient {
  ChatAiReply generate(ChatAiRequest request);

  default void stream(ChatAiRequest request, ChatAiStreamHandler handler) {
    ChatAiReply reply = generate(request);
    handler.onDelta(reply.content());
    handler.onComplete(new ChatAiStreamResult(reply.suggestedFoods(), reply.promptTokens(),
        reply.completionTokens(), reply.modelVersion()));
  }
}
