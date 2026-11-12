package com.healthcare.kallo.service.sys;

import com.healthcare.kallo.model.sys.enums.NotificationType;
import java.util.Map;

public interface NotificationMessageFactory {
  NotificationContent render(NotificationType type, Map<String, Object> params);
}
