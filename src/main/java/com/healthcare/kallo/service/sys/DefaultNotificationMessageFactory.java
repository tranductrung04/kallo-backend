package com.healthcare.kallo.service.sys;

import com.healthcare.kallo.model.sys.enums.NotificationType;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class DefaultNotificationMessageFactory implements NotificationMessageFactory {
  private final MessageSource messages;

  public DefaultNotificationMessageFactory(MessageSource messages) {
    this.messages = messages;
  }

  @Override
  public NotificationContent render(NotificationType type, Locale locale, Map<String, Object> params) {
    Locale selected = locale != null && locale.getLanguage().equalsIgnoreCase("en")
        ? Locale.ENGLISH : Locale.forLanguageTag("vi");
    String prefix = "notifications." + type.name().toLowerCase(Locale.ROOT);
    Object[] args = type == NotificationType.CALORIE_OVER
        ? new Object[] {roundedValue(params, "caloriesIn"), value(params, "thresholdPct")} : new Object[0];
    String title = message(prefix + ".title", selected, args, type.name());
    String body = message(prefix + ".body", selected, args, "Bạn có một thông báo mới");
    String pushTitle = message(prefix + ".push.title", selected, new Object[0], title);
    String pushBody = message(prefix + ".push.body", selected, new Object[0], "Bạn có một thông báo mới");
    return new NotificationContent(title, body, pushTitle, pushBody);
  }

  private String message(String key, Locale locale, Object[] params, String fallback) {
    try {
      return messages.getMessage(key, params, locale);
    } catch (RuntimeException e) {
      return fallback;
    }
  }

  private Object value(Map<String, Object> params, String key) {
    return params == null || params.get(key) == null ? "" : params.get(key);
  }

  private Object roundedValue(Map<String, Object> params, String key) {
    Object value = value(params, key);
    return value instanceof Number number ? Math.round(number.doubleValue()) : value;
  }
}
