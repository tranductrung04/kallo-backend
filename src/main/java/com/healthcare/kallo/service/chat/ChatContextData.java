package com.healthcare.kallo.service.chat;

import java.util.List;
import java.util.Map;

public record ChatContextData(Map<String, Object> profile, Map<String, Object> today,
    List<String> allergies) {
  public ChatContextData {
    profile = profile == null ? Map.of() : Map.copyOf(profile);
    today = today == null ? Map.of() : Map.copyOf(today);
    allergies = allergies == null ? List.of() : List.copyOf(allergies);
  }

  public static ChatContextData empty() {
    return new ChatContextData(Map.of(), Map.of(), List.of());
  }
}
