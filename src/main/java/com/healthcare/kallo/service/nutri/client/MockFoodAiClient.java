package com.healthcare.kallo.service.nutri.client;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Deterministic mock AI recognition client for local development and tests. */
@Component
@ConditionalOnProperty(name = "app.nutri.ai.mock-enabled", havingValue = "true")
public class MockFoodAiClient implements FoodAiClient {

  @Override
  public FoodAiDetectResponse detect(byte[] imageBytes, String filename, String contentType) {
    return new FoodAiDetectResponse(
        true,
        "Banh mi",
        0.95,
        List.of(new DetectedItem("Banh mi", 0.95)));
  }
}
