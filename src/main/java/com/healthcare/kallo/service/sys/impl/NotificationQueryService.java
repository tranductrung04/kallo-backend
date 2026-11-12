package com.healthcare.kallo.service.sys.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.response.sys.*;
import com.healthcare.kallo.model.sys.Notification;
import com.healthcare.kallo.repository.sys.NotificationRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Clock;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationQueryService implements com.healthcare.kallo.service.sys.NotificationQuery {
  private final NotificationRepository notifications;
  private final Clock clock;

  public NotificationQueryService(NotificationRepository notifications, Clock clock) {
    this.notifications = notifications;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public NotificationPageResponse page(UUID userId, String cursor, int limit, boolean unreadOnly) {
    if (limit < 1 || limit > 50) throw new BusinessException(ErrorCode.INVALID_PARAMETER);
    Cursor decoded = Cursor.decode(cursor);
    List<Notification> items =
        decoded.createdAt() == null
            ? notifications.findFirstPage(userId, unreadOnly, PageRequest.of(0, limit + 1))
            : notifications.findPageAfter(
                userId, unreadOnly, decoded.createdAt(), decoded.id(), PageRequest.of(0, limit + 1));
    boolean hasMore = items.size() > limit;
    if (hasMore) items = items.subList(0, limit);
    String next = hasMore ? Cursor.encode(items.get(items.size() - 1)) : null;
    return new NotificationPageResponse(
        items.stream().map(NotificationResponse::from).toList(),
        next,
        notifications.countByUserIdAndReadFalseAndDeletedAtIsNull(userId));
  }

  @Transactional
  public void markRead(UUID userId, UUID id) {
    if (notifications.markRead(id, userId, Instant.now(clock)) == 0) {
      throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
    }
  }

  @Transactional
  public void markAllRead(UUID userId) {
    notifications.markAllRead(userId, Instant.now(clock));
  }

  @Transactional
  public void delete(UUID userId, UUID id) {
    if (notifications.softDelete(id, userId, Instant.now(clock)) == 0) {
      throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public long unreadCount(UUID userId) {
    return notifications.countByUserIdAndReadFalseAndDeletedAtIsNull(userId);
  }

  private record Cursor(Instant createdAt, UUID id) {
    static Cursor decode(String value) {
      if (value == null || value.isBlank()) return new Cursor(null, null);
      try {
        String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        String[] parts = decoded.split("/", 2);
        return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
      } catch (RuntimeException e) {
        throw new BusinessException(ErrorCode.INVALID_PARAMETER);
      }
    }

    static String encode(Notification notification) {
      String value = notification.getCreatedAt() + "/" + notification.getId();
      return Base64.getUrlEncoder().withoutPadding()
          .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
  }
}
