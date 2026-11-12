package com.healthcare.kallo.dto.response.sys;

import com.healthcare.kallo.model.sys.Notification;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    String type,
    String title,
    String body,
    Map<String, Object> data,
    boolean read,
    Instant createdAt) {
  public static NotificationResponse from(Notification n) {
    return new NotificationResponse(
        n.getId(), n.getType().name(), n.getTitle(), n.getBody(), n.getData(), n.isRead(), n.getCreatedAt());
  }
}
