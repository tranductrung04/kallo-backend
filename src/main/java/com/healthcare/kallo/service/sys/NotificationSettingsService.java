package com.healthcare.kallo.service.sys;

import com.healthcare.kallo.dto.request.sys.NotificationSettingsRequest;
import com.healthcare.kallo.model.sys.NotificationSettings;
import java.util.UUID;

public interface NotificationSettingsService {
  NotificationSettings getOrCreate(UUID userId);
  NotificationSettings update(UUID userId, NotificationSettingsRequest request);
}
