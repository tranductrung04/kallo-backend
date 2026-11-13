package com.healthcare.kallo.service.sys;

import com.healthcare.kallo.model.sys.enums.NotificationType;
import java.util.Locale;
import java.util.Map;

public interface NotificationMessageFactory {
  NotificationContent render(NotificationType type, Locale locale, Map<String, Object> params);
}
