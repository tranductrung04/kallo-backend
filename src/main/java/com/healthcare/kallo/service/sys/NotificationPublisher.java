package com.healthcare.kallo.service.sys;

import com.healthcare.kallo.event.sys.NotificationCreatedEvent;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationPublisher {
  private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);
  private final RealtimePublisher realtime;
  private final NotificationQuery notifications;

  public NotificationPublisher(RealtimePublisher realtime, NotificationQuery notifications) {
    this.realtime = realtime;
    this.notifications = notifications;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public void onNotificationCreated(NotificationCreatedEvent event) {
    try {
      long unreadCount = notifications.unreadCount(event.userId());
      realtime.sendToUser(event.userId(), "/queue/notifications", "NOTIFICATION_CREATED",
          new NotificationCreatedMessage(event.notificationId(), unreadCount));
    } catch (RuntimeException e) {
      log.error("Failed to publish WebSocket notification {}", event.notificationId(), e);
    }
  }

  public record NotificationCreatedMessage(UUID id, long unreadCount) {}
}
