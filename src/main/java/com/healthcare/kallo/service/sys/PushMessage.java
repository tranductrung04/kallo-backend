package com.healthcare.kallo.service.sys;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record PushMessage(String type, String notificationId, String title, String body) {
  public PushMessage {
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(notificationId, "notificationId must not be null");
    Objects.requireNonNull(title, "title must not be null");
    Objects.requireNonNull(body, "body must not be null");
  }

  public Map<String, String> data() {
    return Map.of("type", type, "notificationId", notificationId);
  }

  public UUID notificationIdAsUuid() {
    return UUID.fromString(notificationId);
  }
}
