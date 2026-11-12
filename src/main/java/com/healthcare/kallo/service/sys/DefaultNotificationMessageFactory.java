package com.healthcare.kallo.service.sys;

import com.healthcare.kallo.model.sys.enums.NotificationType;
import java.util.Map;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class DefaultNotificationMessageFactory implements NotificationMessageFactory {
  @Override
  public NotificationContent render(NotificationType type, Map<String, Object> params) {
    String title = type.name();
    String body = params != null && params.get("message") != null
        ? String.valueOf(params.get("message")) : "Bạn có một thông báo mới";
    return new NotificationContent(title, body, title, "Bạn có một thông báo mới");
  }
}
