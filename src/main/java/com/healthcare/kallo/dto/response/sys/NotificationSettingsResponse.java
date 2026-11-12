package com.healthcare.kallo.dto.response.sys;

import com.healthcare.kallo.model.sys.NotificationSettings;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public record NotificationSettingsResponse(
    String language, boolean pushEnabled, boolean waterReminderEnabled, String waterStartTime,
    String waterEndTime, int waterIntervalMinutes, boolean mealReminderEnabled,
    String mealBreakfastTime, String mealLunchTime, String mealDinnerTime,
    boolean calorieAlertEnabled, int calorieAlertThresholdPct, boolean socialEnabled,
    boolean dndEnabled, String dndStartTime, String dndEndTime) {
  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

  public static NotificationSettingsResponse from(NotificationSettings s) {
    return new NotificationSettingsResponse(
        s.getLanguage(), s.isPushEnabled(), s.isWaterReminderEnabled(), format(s.getWaterStartTime()),
        format(s.getWaterEndTime()), s.getWaterIntervalMinutes(), s.isMealReminderEnabled(),
        format(s.getMealBreakfastTime()), format(s.getMealLunchTime()), format(s.getMealDinnerTime()),
        s.isCalorieAlertEnabled(), s.getCalorieAlertThresholdPct(), s.isSocialEnabled(),
        s.isDndEnabled(), format(s.getDndStartTime()), format(s.getDndEndTime()));
  }

  private static String format(LocalTime value) {
    return value == null ? null : value.format(TIME_FORMAT);
  }
}
