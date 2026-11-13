package com.healthcare.kallo.model.sys;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "notification_settings")
@Getter
@Setter
public class NotificationSettings {
  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Column(nullable = false, length = 5)
  private String language = "vi";
  @Column(name = "push_enabled", nullable = false)
  private boolean pushEnabled = true;
  @Column(name = "water_reminder_enabled", nullable = false)
  private boolean waterReminderEnabled = true;
  @Column(name = "water_start_time", nullable = false)
  private LocalTime waterStartTime = LocalTime.of(10, 0);
  @Column(name = "water_end_time", nullable = false)
  private LocalTime waterEndTime = LocalTime.of(18, 0);
  @Column(name = "water_interval_minutes", nullable = false)
  private int waterIntervalMinutes = 240;
  @Column(name = "meal_reminder_enabled", nullable = false)
  private boolean mealReminderEnabled = true;
  @Column(name = "meal_breakfast_time")
  private LocalTime mealBreakfastTime;
  @Column(name = "meal_lunch_time")
  private LocalTime mealLunchTime;
  @Column(name = "meal_dinner_time")
  private LocalTime mealDinnerTime;
  @Column(name = "calorie_alert_enabled", nullable = false)
  private boolean calorieAlertEnabled = true;
  @Column(name = "calorie_alert_threshold_pct", nullable = false)
  private int calorieAlertThresholdPct = 100;
  @Column(name = "social_enabled", nullable = false)
  private boolean socialEnabled = true;
  @Column(name = "dnd_enabled", nullable = false)
  private boolean dndEnabled;
  @Column(name = "dnd_start_time", nullable = false)
  private LocalTime dndStartTime = LocalTime.of(22, 0);
  @Column(name = "dnd_end_time", nullable = false)
  private LocalTime dndEndTime = LocalTime.of(7, 0);
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }
}
