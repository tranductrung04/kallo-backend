package com.healthcare.kallo.service.sys;

import com.healthcare.kallo.dto.response.sys.NotificationPageResponse;
import java.util.UUID;

public interface NotificationQuery {
  NotificationPageResponse page(UUID userId, String cursor, int limit, boolean unreadOnly);
  long unreadCount(UUID userId);
  void markRead(UUID userId, UUID id);
  void markAllRead(UUID userId);
  void delete(UUID userId, UUID id);
}
