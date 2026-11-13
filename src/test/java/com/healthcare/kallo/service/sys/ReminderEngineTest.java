package com.healthcare.kallo.service.sys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.healthcare.kallo.config.NotificationProperties;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.sys.NotificationSettings;
import com.healthcare.kallo.model.sys.enums.NotificationType;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.sys.NotificationRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReminderEngineTest {
  private final UserRepository users = mock(UserRepository.class);
  private final NotificationSettingsService settingsService = mock(NotificationSettingsService.class);
  private final NotificationRepository notifications = mock(NotificationRepository.class);
  private final UserActivityPort activity = mock(UserActivityPort.class);
  private final NotificationService notificationService = mock(NotificationService.class);
  private final UUID userId = UUID.randomUUID();
  private ReminderEngine engine;

  @BeforeEach
  void setUp() {
    User user = new User();
    user.setId(userId);
    user.setEmailVerified(true);
    user.setTimezone("Asia/Ho_Chi_Minh");
    when(users.findReminderCandidates(any(), any())).thenReturn(List.of(user));
    when(settingsService.getOrCreate(userId)).thenReturn(new NotificationSettings());
    when(notifications.countByUserIdAndTypesAndCreatedAtBetween(any(), any(), any(), any()))
        .thenReturn(0L);
    engine = new ReminderEngine(
        users, settingsService, notifications, activity, notificationService,
        new NotificationProperties(30, 6, 10, 60, Duration.ofMinutes(10)));
  }

  @Test
  void processesOneHundredReminderCandidates() {
    List<User> candidates = IntStream.range(0, 100).mapToObj(index -> {
      User user = new User();
      user.setId(UUID.randomUUID());
      user.setEmailVerified(true);
      user.setTimezone("Asia/Ho_Chi_Minh");
      return user;
    }).toList();
    when(users.findReminderCandidates(any(), any())).thenReturn(candidates);
    for (User user : candidates) {
      when(settingsService.getOrCreate(user.getId())).thenReturn(new NotificationSettings());
      when(notifications.countByUserIdAndTypesAndCreatedAtBetween(eq(user.getId()), any(), any(), any()))
          .thenReturn(6L);
    }

    long started = System.nanoTime();
    engine.run(Instant.parse("2026-11-12T07:05:00Z"));

    assertThat(candidates).hasSize(100);
    verify(settingsService, times(100)).getOrCreate(any());
    verify(notificationService, never()).notify(any());
    assertThat(System.nanoTime() - started).isPositive();
  }

  @Test
  void sendsWaterReminderInTheUsersLocalCatchUpWindow() {
    engine.run(Instant.parse("2026-11-12T07:05:00Z"));

    ArgumentCaptor<NotifyCommand> command = ArgumentCaptor.forClass(NotifyCommand.class);
    verify(notificationService).notify(command.capture());
    assertThat(command.getValue().type()).isEqualTo(NotificationType.WATER_REMINDER);
    assertThat(command.getValue().dedupeKey()).isEqualTo("WATER_REMINDER:2026-11-12:14:00");
  }

  @Test
  void doesNotRemindWaterWhenRecentlyLogged() {
    when(activity.lastWaterLogAt(userId)).thenReturn(
        java.util.Optional.of(Instant.parse("2026-11-12T06:00:00Z")));

    engine.run(Instant.parse("2026-11-12T07:05:00Z"));

    verify(notificationService, never()).notify(any());
  }

  @Test
  void usesMealReminderDefaultsAndDoesNotRemindLoggedMeal() {
    when(activity.lastWaterLogAt(userId)).thenReturn(
        java.util.Optional.of(Instant.parse("2026-11-12T00:00:00Z")));
    when(activity.hasMealLog(userId, java.time.LocalDate.of(2026, 11, 12), "LUNCH"))
        .thenReturn(true);

    engine.run(Instant.parse("2026-11-12T05:35:00Z"));

    verify(notificationService, never()).notify(any());
  }

  @Test
  void customWaterWindowOverridesDefaults() {
    NotificationSettings settings = settings();
    settings.setWaterStartTime(java.time.LocalTime.of(9, 0));
    settings.setWaterEndTime(java.time.LocalTime.of(11, 0));
    settings.setWaterIntervalMinutes(60);

    engine.run(Instant.parse("2026-11-12T02:05:00Z"));

    ArgumentCaptor<NotifyCommand> command = ArgumentCaptor.forClass(NotifyCommand.class);
    verify(notificationService).notify(command.capture());
    assertThat(command.getValue().dedupeKey()).isEqualTo("WATER_REMINDER:2026-11-12:09:00");
  }

  @Test
  void dndSuppressesDueReminder() {
    NotificationSettings settings = settings();
    settings.setDndEnabled(true);
    settings.setDndStartTime(java.time.LocalTime.of(14, 0));
    settings.setDndEndTime(java.time.LocalTime.of(7, 0));

    engine.run(Instant.parse("2026-11-12T07:05:00Z"));

    verify(notificationService, never()).notify(any());
  }

  @Test
  void dailyReminderCapSuppressesDueReminder() {
    when(notifications.countByUserIdAndTypesAndCreatedAtBetween(any(), any(), any(), any()))
        .thenReturn(6L);

    engine.run(Instant.parse("2026-11-12T07:05:00Z"));

    verify(notificationService, never()).notify(any());
  }

  @Test
  void mealPayloadIdentifiesMealType() {
    when(activity.lastWaterLogAt(userId)).thenReturn(
        java.util.Optional.of(Instant.parse("2026-11-12T00:00:00Z")));
    engine.run(Instant.parse("2026-11-12T01:35:00Z"));

    ArgumentCaptor<NotifyCommand> command = ArgumentCaptor.forClass(NotifyCommand.class);
    verify(notificationService).notify(command.capture());
    assertThat(command.getValue().params()).containsEntry("meal", "BREAKFAST");
    assertThat(command.getValue().data()).containsEntry("meal", "BREAKFAST");
  }

  private NotificationSettings settings() {
    NotificationSettings settings = new NotificationSettings();
    when(settingsService.getOrCreate(userId)).thenReturn(settings);
    return settings;
  }
}
