package com.healthcare.kallo.service.sys;

import com.healthcare.kallo.model.sys.enums.NotificationType;
import com.healthcare.kallo.model.sys.NotificationSettings;
import java.time.LocalTime;

public interface NotificationPolicy {
  NotifyOutcome evaluate(NotificationSettings settings, NotificationType type, LocalTime localNow);
}
