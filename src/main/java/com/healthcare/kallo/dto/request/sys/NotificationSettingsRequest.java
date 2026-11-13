package com.healthcare.kallo.dto.request.sys;

import jakarta.validation.constraints.*;

public record NotificationSettingsRequest(
    @NotBlank @Pattern(regexp = "^(vi|en)$") String language,
    @NotNull Boolean pushEnabled,
    @NotNull Boolean waterReminderEnabled,
    @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String waterStartTime,
    @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String waterEndTime,
    @NotNull @Min(30) @Max(720) Integer waterIntervalMinutes,
    @NotNull Boolean mealReminderEnabled,
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String mealBreakfastTime,
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String mealLunchTime,
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String mealDinnerTime,
    @NotNull Boolean calorieAlertEnabled,
    @NotNull @Min(100) @Max(200) Integer calorieAlertThresholdPct,
    @NotNull Boolean socialEnabled,
    @NotNull Boolean dndEnabled,
    @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String dndStartTime,
    @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String dndEndTime) {}
