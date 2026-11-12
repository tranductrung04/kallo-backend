package com.healthcare.kallo.service.sys;

import com.healthcare.kallo.event.sys.NotificationCreatedEvent;
import com.healthcare.kallo.repository.sys.NotificationRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class NotificationPushListener {
  private final PushDispatcher pushes;
  private final NotificationRepository notifications;
  private static final Logger log = LoggerFactory.getLogger(NotificationPushListener.class);

  public NotificationPushListener(PushDispatcher pushes, NotificationRepository notifications) {
    this.pushes = pushes;
    this.notifications = notifications;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onNotificationCreated(NotificationCreatedEvent event) {
    try {
      pushes.dispatch(
          event.userId(),
          new PushMessage(
              event.type().name(),
              event.notificationId().toString(),
              event.pushTitle(),
              event.pushBody()));
    } catch (RuntimeException e) {
      log.error("Failed to dispatch notification {}", event.notificationId(), e);
    }
  }
}
