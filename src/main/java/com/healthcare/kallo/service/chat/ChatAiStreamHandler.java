package com.healthcare.kallo.service.chat;

public interface ChatAiStreamHandler {
  void onDelta(String text);

  void onComplete(ChatAiStreamResult result);
}
