package com.healthcare.kallo.service.sys.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.request.sys.NotificationSettingsRequest;
import com.healthcare.kallo.model.sys.NotificationSettings;
import com.healthcare.kallo.repository.sys.NotificationSettingsRepository;
import com.healthcare.kallo.service.sys.NotificationSettingsService;
import java.time.LocalTime;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationSettingsServiceImpl implements NotificationSettingsService {
  private final NotificationSettingsRepository repository;

  public NotificationSettingsServiceImpl(NotificationSettingsRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional
  public NotificationSettings getOrCreate(UUID userId) {
    var existing = repository.findById(userId);
    if (existing.isPresent()) return existing.get();
    repository.insertDefaults(userId);
    return repository.findById(userId)
        .orElseThrow(() -> new IllegalStateException("Notification settings could not be created"));
  }

  @Override
  @Transactional
  public NotificationSettings update(UUID userId, NotificationSettingsRequest request) {
    LocalTime waterStart = parse(request.waterStartTime());
    LocalTime waterEnd = parse(request.waterEndTime());
    LocalTime dndStart = parse(request.dndStartTime());
    LocalTime dndEnd = parse(request.dndEndTime());
    LinkedHashMap<String, String> details = new LinkedHashMap<>();
    if (!waterEnd.isAfter(waterStart)) details.put("waterEndTime", "must be after waterStartTime");
    if (dndStart.equals(dndEnd)) details.put("dndEndTime", "must differ from dndStartTime");
    if (!details.isEmpty()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, details);

    NotificationSettings settings = getOrCreate(userId);
    settings.setLanguage(request.language().toLowerCase(Locale.ROOT));
    settings.setPushEnabled(request.pushEnabled());
    settings.setWaterReminderEnabled(request.waterReminderEnabled());
    settings.setWaterStartTime(waterStart);
    settings.setWaterEndTime(waterEnd);
    settings.setWaterIntervalMinutes(request.waterIntervalMinutes());
    settings.setMealReminderEnabled(request.mealReminderEnabled());
    settings.setMealBreakfastTime(parseNullable(request.mealBreakfastTime()));
    settings.setMealLunchTime(parseNullable(request.mealLunchTime()));
    settings.setMealDinnerTime(parseNullable(request.mealDinnerTime()));
    settings.setCalorieAlertEnabled(request.calorieAlertEnabled());
    settings.setCalorieAlertThresholdPct(request.calorieAlertThresholdPct());
    settings.setSocialEnabled(request.socialEnabled());
    settings.setDndEnabled(request.dndEnabled());
    settings.setDndStartTime(dndStart);
    settings.setDndEndTime(dndEnd);
    return repository.save(settings);
  }

  private LocalTime parse(String value) {
    try {
      return LocalTime.parse(value);
    } catch (RuntimeException e) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, java.util.Map.of("time", "must use HH:mm"));
    }
  }

  private LocalTime parseNullable(String value) {
    return value == null || value.isBlank() ? null : parse(value);
  }
}
