package com.healthcare.kallo.service.nutri.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Client abstraction for invoking Modal food recognition endpoint. */
public interface FoodAiClient {

  FoodAiDetectResponse detect(byte[] imageBytes, String filename, String contentType);

  @JsonIgnoreProperties(ignoreUnknown = true)
  record FoodAiDetectResponse(
      @JsonProperty("detected") boolean detected,
      @JsonProperty("label") String label,
      @JsonProperty("confidence") Double confidence,
      @JsonProperty("all_items") List<DetectedItem> allItems) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record DetectedItem(
      @JsonProperty("label") String label,
      @JsonProperty("confidence") Double confidence) {}
}
