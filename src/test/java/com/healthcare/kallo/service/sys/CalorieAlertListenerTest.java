package com.healthcare.kallo.service.sys;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.kallo.event.sys.DailySummaryChangedEvent;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.sys.NotificationSettings;
import com.healthcare.kallo.model.sys.enums.NotificationType;
import com.healthcare.kallo.repository.auth.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CalorieAlertListenerTest {
  @Mock NotificationService notifications;
  @Mock NotificationSettingsService settingsService;
  @Mock UserRepository users;

  private CalorieAlertListener listener;
  private UUID userId;
  private User user;
  private NotificationSettings settings;

  @BeforeEach
  void setUp() {
    listener = new CalorieAlertListener(
        notifications,
        settingsService,
        users,
        Clock.fixed(Instant.parse("2026-11-12T17:30:00Z"), ZoneOffset.UTC));
    userId = UUID.randomUUID();
    user = new User();
    user.setId(userId);
    user.setTimezone("Asia/Ho_Chi_Minh");
    settings = new NotificationSettings();
    when(users.findById(userId)).thenReturn(Optional.of(user));
    lenient().when(settingsService.getOrCreate(userId)).thenReturn(settings);
  }

  @Test
  void sendsOneAlertCommandWithThresholdAndStableDedupeKey() {
    DailySummaryChangedEvent event =
        new DailySummaryChangedEvent(userId, LocalDate.of(2026, 11, 13), 2100, 2000);

    listener.onDailySummaryChanged(event);

    ArgumentCaptor<NotifyCommand> captor = ArgumentCaptor.forClass(NotifyCommand.class);
    verify(notifications).notify(captor.capture());
    NotifyCommand command = captor.getValue();
    assertThat(command.type()).isEqualTo(NotificationType.CALORIE_OVER);
    assertThat(command.dedupeKey())
        .isEqualTo("CALORIE_OVER:2026-11-13:100");
    assertThat(command.params())
        .containsEntry("caloriesIn", 2100.0)
        .containsEntry("thresholdPct", 100);
    assertThat(command.data()).isEmpty();
  }

  @Test
  void ignoresCaloriesAtThreshold() {
    listener.onDailySummaryChanged(event(2000, 2000));

    verifyNoInteractions(notifications);
  }

  @Test
  void ignoresBelowThreshold() {
    listener.onDailySummaryChanged(event(1999, 2000));

    verifyNoInteractions(notifications);
  }

  @Test
  void ignoresDisabledAlerts() {
    settings.setCalorieAlertEnabled(false);
    listener.onDailySummaryChanged(event(2200, 2000));

    verifyNoInteractions(notifications);
  }

  @Test
  void ignoresInvalidGoals() {
    listener.onDailySummaryChanged(event(2200, 0));

    verifyNoInteractions(notifications);
  }

  @Test
  void ignoresEventsThatAreNotTodayInUsersTimezone() {
    listener.onDailySummaryChanged(
        new DailySummaryChangedEvent(userId, LocalDate.of(2026, 11, 12), 2200, 2000));

    verifyNoInteractions(notifications);
  }

  @Test
  void usesConfiguredThresholdAsDistinctAlertLevel() {
    settings.setCalorieAlertThresholdPct(120);
    listener.onDailySummaryChanged(event(2200, 2000));
    listener.onDailySummaryChanged(event(2401, 2000));

    ArgumentCaptor<NotifyCommand> captor = ArgumentCaptor.forClass(NotifyCommand.class);
    verify(notifications).notify(captor.capture());
    assertThat(captor.getValue().dedupeKey())
        .isEqualTo("CALORIE_OVER:2026-11-13:120");
  }

  @Test
  void fallsBackToVietnamTimezoneWhenUserTimezoneIsInvalid() {
    user.setTimezone("not-a-zone");
    listener.onDailySummaryChanged(event(2100, 2000));

    verify(notifications).notify(any(NotifyCommand.class));
  }

  @Test
  void ignoresUnknownUsers() {
    when(users.findById(userId)).thenReturn(Optional.empty());

    listener.onDailySummaryChanged(event(2100, 2000));

    verifyNoInteractions(settingsService, notifications);
  }

  @Test
  void notificationFailureDoesNotEscapeListener() {
    doThrow(new IllegalArgumentException("notification failed"))
        .when(notifications).notify(any(NotifyCommand.class));

    listener.onDailySummaryChanged(event(2100, 2000));

    verify(notifications).notify(any(NotifyCommand.class));
  }

  private DailySummaryChangedEvent event(double caloriesIn, double calorieGoal) {
    return new DailySummaryChangedEvent(
        userId, LocalDate.of(2026, 11, 13), caloriesIn, calorieGoal);
  }
}
