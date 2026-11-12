package com.healthcare.kallo.service.sys;

import com.healthcare.kallo.model.sys.enums.NotificationType;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AllowAllNotificationPolicy implements NotificationPolicy {
  @Override
  public NotifyOutcome evaluate(UUID userId, NotificationType type) {
    return NotifyOutcome.SENT;
  }
}
