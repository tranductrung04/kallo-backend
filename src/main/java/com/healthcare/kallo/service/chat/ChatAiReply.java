package com.healthcare.kallo.service.chat;

import java.util.List;

public record ChatAiReply(String content, List<SuggestedFood> suggestedFoods, Integer promptTokens,
    Integer completionTokens, String modelVersion) {
  public ChatAiReply {
    suggestedFoods = suggestedFoods == null ? List.of() : List.copyOf(suggestedFoods);
  }

  public record SuggestedFood(String name, Integer kcal) {}
}
