package com.healthcare.kallo.service.sys;

import com.healthcare.kallo.event.sys.NotificationCreatedEvent;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationPublisher {
  private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);
  private final SimpMessagingTemplate messaging;
  private final NotificationQuery notifications;

  public NotificationPublisher(SimpMessagingTemplate messaging, NotificationQuery notifications) {
    this.messaging = messaging;
    this.notifications = notifications;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public void onNotificationCreated(NotificationCreatedEvent event) {
    try {
      long unreadCount = notifications.unreadCount(event.userId());
      messaging.convertAndSendToUser(
          event.userId().toString(),
          "/queue/notifications",
          new NotificationCreatedMessage("NOTIFICATION_CREATED", event.notificationId(), unreadCount));
    } catch (RuntimeException e) {
      log.error("Failed to publish WebSocket notification {}", event.notificationId(), e);
    }
  }

  public record NotificationCreatedMessage(String type, UUID id, long unreadCount) {}
}
