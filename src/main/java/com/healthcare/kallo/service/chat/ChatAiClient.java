package com.healthcare.kallo.service.chat;

import java.util.List;

public interface ChatAiClient {
  ChatAiReply generate(ChatAiRequest request);

  default void stream(ChatAiRequest request, ChatAiStreamHandler handler) {
    ChatAiReply reply = generate(request);
    handler.onDelta(reply.content());
    handler.onComplete(new ChatAiStreamResult(reply.suggestedFoods(), reply.promptTokens(),
        reply.completionTokens(), reply.modelVersion()));
  }

  default String summarize(String existingSummary, List<ChatAiRequest.ChatHistoryMessage> olderMessages) {
    return null;
  }
}
