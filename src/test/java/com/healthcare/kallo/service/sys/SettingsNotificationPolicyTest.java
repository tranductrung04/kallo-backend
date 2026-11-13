package com.healthcare.kallo.service.sys;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.kallo.model.sys.NotificationSettings;
import com.healthcare.kallo.model.sys.enums.NotificationType;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class SettingsNotificationPolicyTest {
  private final SettingsNotificationPolicy policy = new SettingsNotificationPolicy();

  @Test
  void handlesOvernightDndBoundaries() {
    NotificationSettings settings = settings();
    assertThat(policy.evaluate(settings, NotificationType.SYSTEM, LocalTime.of(21, 59))).isEqualTo(NotifyOutcome.SENT);
    assertThat(policy.evaluate(settings, NotificationType.SYSTEM, LocalTime.of(22, 0))).isEqualTo(NotifyOutcome.SUPPRESSED_PUSH_ONLY);
    assertThat(policy.evaluate(settings, NotificationType.SYSTEM, LocalTime.of(23, 30))).isEqualTo(NotifyOutcome.SUPPRESSED_PUSH_ONLY);
    assertThat(policy.evaluate(settings, NotificationType.SYSTEM, LocalTime.of(6, 59))).isEqualTo(NotifyOutcome.SUPPRESSED_PUSH_ONLY);
    assertThat(policy.evaluate(settings, NotificationType.SYSTEM, LocalTime.of(7, 0))).isEqualTo(NotifyOutcome.SENT);
  }

  @Test
  void disabledTypesAreSuppressedAndRemindersDuringDndAreDropped() {
    NotificationSettings settings = settings();
    settings.setWaterReminderEnabled(false);
    assertThat(policy.evaluate(settings, NotificationType.WATER_REMINDER, LocalTime.of(12, 0)))
        .isEqualTo(NotifyOutcome.SUPPRESSED_DISABLED);

    settings.setWaterReminderEnabled(true);
    assertThat(policy.evaluate(settings, NotificationType.WATER_REMINDER, LocalTime.of(23, 30)))
        .isEqualTo(NotifyOutcome.SUPPRESSED_DND_REMINDER);
  }

  private NotificationSettings settings() {
    NotificationSettings settings = new NotificationSettings();
    settings.setDndEnabled(true);
    settings.setDndStartTime(LocalTime.of(22, 0));
    settings.setDndEndTime(LocalTime.of(7, 0));
    return settings;
  }
}
