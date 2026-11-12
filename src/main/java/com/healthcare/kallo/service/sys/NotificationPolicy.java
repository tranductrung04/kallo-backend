package com.healthcare.kallo.service.sys;

import com.healthcare.kallo.model.sys.enums.NotificationType;
import java.util.UUID;

public interface NotificationPolicy {
  NotifyOutcome evaluate(UUID userId, NotificationType type);
}
