package com.healthcare.kallo.service.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

public record ChatAiReply(
    String content,
    List<SuggestedFood> suggestedFoods,
    Integer promptTokens,
    Integer completionTokens,
    String modelVersion,
    String intent,
    boolean disclaimerAdded,
    String updatedSummary,
    String suggestedTitle) {

  public ChatAiReply {
    suggestedFoods = suggestedFoods == null ? List.of() : List.copyOf(suggestedFoods);
  }

  public ChatAiReply(
      String content,
      List<SuggestedFood> suggestedFoods,
      Integer promptTokens,
      Integer completionTokens,
      String modelVersion) {
    this(content, suggestedFoods, promptTokens, completionTokens, modelVersion, null, false, null, null);
  }

  public ChatAiReply withContentAndFoods(String newContent, List<SuggestedFood> newFoods, boolean newDisclaimerAdded) {
    return new ChatAiReply(
        newContent,
        newFoods,
        promptTokens,
        completionTokens,
        modelVersion,
        intent,
        disclaimerAdded || newDisclaimerAdded,
        updatedSummary,
        suggestedTitle);
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SuggestedFood(UUID foodId, String name, Integer kcal) {
    public SuggestedFood(String name, Integer kcal) {
      this(null, name, kcal);
    }
  }
}
