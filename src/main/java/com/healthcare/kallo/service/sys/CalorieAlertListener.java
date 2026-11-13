package com.healthcare.kallo.service.sys;

import com.healthcare.kallo.event.sys.DailySummaryChangedEvent;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.sys.NotificationSettings;
import com.healthcare.kallo.model.sys.enums.NotificationType;
import com.healthcare.kallo.repository.auth.UserRepository;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CalorieAlertListener {
  private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final Logger log = LoggerFactory.getLogger(CalorieAlertListener.class);

  private final NotificationService notifications;
  private final NotificationSettingsService settingsService;
  private final UserRepository users;
  private final Clock clock;

  public CalorieAlertListener(
      NotificationService notifications,
      NotificationSettingsService settingsService,
      UserRepository users,
      Clock clock) {
    this.notifications = notifications;
    this.settingsService = settingsService;
    this.users = users;
    this.clock = clock;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onDailySummaryChanged(DailySummaryChangedEvent event) {
    try {
      User user = users.findById(event.userId()).orElse(null);
      if (user == null) return;

      ZoneId zone = zoneOrDefault(user.getTimezone());
      LocalDate today = LocalDate.now(clock.withZone(zone));
      if (!today.equals(event.date()) || event.calorieGoal() <= 0) return;

      NotificationSettings settings = settingsService.getOrCreate(event.userId());
      if (!settings.isCalorieAlertEnabled()) return;

      int threshold = settings.getCalorieAlertThresholdPct();
      if (!(event.caloriesIn() > event.calorieGoal() * threshold / 100.0)) return;

      notifications.notify(new NotifyCommand(
          event.userId(),
          NotificationType.CALORIE_OVER,
          Map.of(
              "caloriesIn", event.caloriesIn(),
              "thresholdPct", threshold),
          Map.of(),
          "CALORIE_OVER:" + event.date() + ":" + threshold));
    } catch (RuntimeException e) {
      log.warn("Calorie alert failed for user {}", event.userId());
    }
  }

  private ZoneId zoneOrDefault(String timezone) {
    try {
      return ZoneId.of(timezone == null ? DEFAULT_ZONE.getId() : timezone);
    } catch (DateTimeException e) {
      return DEFAULT_ZONE;
    }
  }
}
