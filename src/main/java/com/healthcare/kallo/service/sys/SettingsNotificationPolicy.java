package com.healthcare.kallo.service.sys;

import com.healthcare.kallo.model.sys.NotificationSettings;
import com.healthcare.kallo.model.sys.enums.NotificationType;
import java.time.LocalTime;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class SettingsNotificationPolicy implements NotificationPolicy {
  @Override
  public NotifyOutcome evaluate(NotificationSettings settings, NotificationType type, LocalTime localNow) {
    if (!enabled(settings, type)) return NotifyOutcome.SUPPRESSED_DISABLED;
    boolean dnd = settings.isDndEnabled() && inWindow(localNow, settings.getDndStartTime(), settings.getDndEndTime());
    if (dnd && type.kind() == NotificationType.Kind.REMINDER) {
      return NotifyOutcome.SUPPRESSED_DND_REMINDER;
    }
    if (!settings.isPushEnabled() || dnd) return NotifyOutcome.SUPPRESSED_PUSH_ONLY;
    return NotifyOutcome.SENT;
  }

  private boolean enabled(NotificationSettings settings, NotificationType type) {
    return switch (type) {
      case WATER_REMINDER -> settings.isWaterReminderEnabled();
      case MEAL_REMINDER -> settings.isMealReminderEnabled();
      case CALORIE_OVER -> settings.isCalorieAlertEnabled();
      case BADGE_EARNED, FRIEND_REQUEST, FRIEND_OVERTAKE, CHALLENGE_EXPIRING, CHALLENGE_COMPLETED ->
          settings.isSocialEnabled();
      case SYSTEM -> true;
    };
  }

  static boolean inWindow(LocalTime now, LocalTime start, LocalTime end) {
    if (start.equals(end)) return false;
    return start.isBefore(end)
        ? !now.isBefore(start) && now.isBefore(end)
        : !now.isBefore(start) || now.isBefore(end);
  }
}
