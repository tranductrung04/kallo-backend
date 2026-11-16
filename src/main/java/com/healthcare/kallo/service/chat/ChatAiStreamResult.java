package com.healthcare.kallo.service.chat;

import java.util.List;

public record ChatAiStreamResult(List<ChatAiReply.SuggestedFood> suggestedFoods, Integer promptTokens,
    Integer completionTokens, String modelVersion) {
  public ChatAiStreamResult {
    suggestedFoods = suggestedFoods == null ? List.of() : List.copyOf(suggestedFoods);
  }
}
