package com.healthcare.kallo.service.sys;

import com.healthcare.kallo.config.NotificationProperties;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.auth.enums.UserStatus;
import com.healthcare.kallo.model.sys.NotificationSettings;
import com.healthcare.kallo.model.sys.enums.NotificationType;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.sys.NotificationRepository;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ReminderEngine {
  private static final Logger log = LoggerFactory.getLogger(ReminderEngine.class);
  private static final int CANDIDATE_PAGE_SIZE = 200;
  private static final LocalTime DEFAULT_BREAKFAST = LocalTime.of(8, 30);
  private static final LocalTime DEFAULT_LUNCH = LocalTime.of(12, 30);
  private static final LocalTime DEFAULT_DINNER = LocalTime.of(19, 30);

  private final UserRepository users;
  private final NotificationSettingsService settingsService;
  private final NotificationRepository notifications;
  private final UserActivityPort activity;
  private final NotificationService notificationService;
  private final NotificationProperties properties;

  public ReminderEngine(
      UserRepository users,
      NotificationSettingsService settingsService,
      NotificationRepository notifications,
      UserActivityPort activity,
      NotificationService notificationService,
      NotificationProperties properties) {
    this.users = users;
    this.settingsService = settingsService;
    this.notifications = notifications;
    this.activity = activity;
    this.notificationService = notificationService;
    this.properties = properties;
  }

  public void run(Instant now) {
    int page = 0;
    List<User> candidates;
    do {
      candidates = users.findReminderCandidates(
          UserStatus.ACTIVE, PageRequest.of(page++, CANDIDATE_PAGE_SIZE));
      for (User user : candidates) {
        try {
          processUser(user, now);
        } catch (RuntimeException error) {
          log.warn("Reminder processing failed for user {}", user.getId(), error);
        }
      }
    } while (candidates.size() == CANDIDATE_PAGE_SIZE);
  }

  private void processUser(User user, Instant now) {
    UUID userId = user.getId();
    NotificationSettings settings = settingsService.getOrCreate(userId);
    ZoneId zone = zoneOrDefault(user.getTimezone());
    ZonedDateTime localNow = now.atZone(zone);
    LocalDate date = localNow.toLocalDate();
    LocalDateTime localDateTime = localNow.toLocalDateTime();
    boolean waterDue = settings.isWaterReminderEnabled()
        && dueWaterSlot(localDateTime, settings) != null;
    boolean breakfastDue = settings.isMealReminderEnabled()
        && isDue(localDateTime, LocalDateTime.of(date,
            settings.getMealBreakfastTime() == null ? DEFAULT_BREAKFAST : settings.getMealBreakfastTime()));
    boolean lunchDue = settings.isMealReminderEnabled()
        && isDue(localDateTime, LocalDateTime.of(date,
            settings.getMealLunchTime() == null ? DEFAULT_LUNCH : settings.getMealLunchTime()));
    boolean dinnerDue = settings.isMealReminderEnabled()
        && isDue(localDateTime, LocalDateTime.of(date,
            settings.getMealDinnerTime() == null ? DEFAULT_DINNER : settings.getMealDinnerTime()));
    if (!waterDue && !breakfastDue && !lunchDue && !dinnerDue) return;
    if (isDnd(settings, localNow.toLocalTime())) return;
    if (reminderCount(userId, localNow) >= properties.reminderDailyCap()) return;

    LocalTime waterSlot = dueWaterSlot(localDateTime, settings);
    if (waterSlot != null && shouldRemindWater(userId, settings, now)) {
      notifyIfCapacity(userId, NotificationType.WATER_REMINDER,
          "WATER_REMINDER:" + date + ":" + waterSlot, Map.of(), Map.of());
    }

    if (!settings.isMealReminderEnabled()) return;
    if (breakfastDue) remindMeal(userId, localNow, "BREAKFAST");
    if (lunchDue) remindMeal(userId, localNow, "LUNCH");
    if (dinnerDue) remindMeal(userId, localNow, "DINNER");
  }

  private void remindMeal(UUID userId, ZonedDateTime localNow, String mealType) {
    if (reminderCount(userId, localNow) >= properties.reminderDailyCap()) return;
    if (activity.hasMealLog(userId, localNow.toLocalDate(), mealType)) return;
    notifyIfCapacity(userId, NotificationType.MEAL_REMINDER,
        "MEAL_REMINDER:" + localNow.toLocalDate() + ":" + mealType,
        Map.of("meal", mealType), Map.of("meal", mealType));
  }

  private boolean shouldRemindWater(UUID userId, NotificationSettings settings, Instant now) {
    return activity.lastWaterLogAt(userId)
        .map(last -> Duration.between(last, now).compareTo(
            Duration.ofMinutes(Math.max(1, settings.getWaterIntervalMinutes() / 2))) >= 0)
        .orElse(true);
  }

  private void notifyIfCapacity(UUID userId, NotificationType type, String dedupeKey,
      Map<String, Object> params, Map<String, String> data) {
    notificationService.notify(new NotifyCommand(userId, type, params, data, dedupeKey));
  }

  private long reminderCount(UUID userId, ZonedDateTime localNow) {
    Instant from = localNow.toLocalDate().atStartOfDay(localNow.getZone()).toInstant();
    Instant to = localNow.toLocalDate().plusDays(1).atStartOfDay(localNow.getZone()).toInstant();
    return notifications.countByUserIdAndTypesAndCreatedAtBetween(
        userId, java.util.Arrays.stream(NotificationType.values())
            .filter(type -> type.kind() == NotificationType.Kind.REMINDER).toList(), from, to);
  }

  private LocalTime dueWaterSlot(LocalDateTime localNow, NotificationSettings settings) {
    LocalTime slot = settings.getWaterStartTime();
    LocalTime end = settings.getWaterEndTime();
    while (!slot.isAfter(end)) {
      if (isDue(localNow, LocalDateTime.of(localNow.toLocalDate(), slot))) return slot;
      LocalTime next = slot.plusMinutes(settings.getWaterIntervalMinutes());
      if (!next.isAfter(slot)) return null;
      slot = next;
    }
    return null;
  }

  private boolean isDue(LocalDateTime now, LocalDateTime slot) {
    LocalDateTime end = slot.plus(properties.reminderCatchUp());
    return !now.isBefore(slot) && !now.isAfter(end);
  }

  private boolean isDnd(NotificationSettings settings, LocalTime now) {
    return settings.isDndEnabled()
        && SettingsNotificationPolicy.inWindow(now, settings.getDndStartTime(), settings.getDndEndTime());
  }

  private ZoneId zoneOrDefault(String timezone) {
    try {
      return ZoneId.of(timezone == null ? "Asia/Ho_Chi_Minh" : timezone);
    } catch (DateTimeException e) {
      return ZoneId.of("Asia/Ho_Chi_Minh");
    }
  }
}
