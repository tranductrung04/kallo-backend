package com.healthcare.kallo.service.chat;

import java.util.List;

public record ChatAiStreamResult(
    List<ChatAiReply.SuggestedFood> suggestedFoods,
    Integer promptTokens,
    Integer completionTokens,
    String modelVersion,
    String intent,
    boolean disclaimerAdded,
    String updatedSummary,
    String suggestedTitle) {

  public ChatAiStreamResult {
    suggestedFoods = suggestedFoods == null ? List.of() : List.copyOf(suggestedFoods);
  }

  public ChatAiStreamResult(
      List<ChatAiReply.SuggestedFood> suggestedFoods,
      Integer promptTokens,
      Integer completionTokens,
      String modelVersion) {
    this(suggestedFoods, promptTokens, completionTokens, modelVersion, null, false, null, null);
  }
}
