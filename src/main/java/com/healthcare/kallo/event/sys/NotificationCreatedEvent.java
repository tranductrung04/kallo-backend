package com.healthcare.kallo.event.sys;

import com.healthcare.kallo.model.sys.enums.NotificationType;
import java.util.UUID;

public record NotificationCreatedEvent(
    UUID notificationId,
    UUID userId,
    NotificationType type,
    String pushTitle,
    String pushBody) {}
