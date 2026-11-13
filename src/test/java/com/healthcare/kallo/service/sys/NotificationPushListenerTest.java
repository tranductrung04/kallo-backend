package com.healthcare.kallo.service.sys;

import static org.mockito.Mockito.*;

import com.healthcare.kallo.event.sys.NotificationCreatedEvent;
import com.healthcare.kallo.model.sys.enums.NotificationType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationPushListenerTest {
  @Mock PushDispatcher pushes;

  @Test
  void dispatchesOnlyAfterEventAndDoesNotMarkBeforePushCompletes() {
    var listener = new NotificationPushListener(pushes, null);
    UUID notificationId = UUID.randomUUID();

    listener.onNotificationCreated(
        new NotificationCreatedEvent(
            notificationId, UUID.randomUUID(), NotificationType.SYSTEM, "generic", "new notification"));

    verify(pushes).dispatch(any(), argThat(message -> message.notificationId().equals(notificationId.toString())));
  }

  @Test
  void pushSchedulingFailureIsContained() {
    doThrow(new IllegalStateException("executor unavailable"))
        .when(pushes)
        .dispatch(any(), any());
    var listener = new NotificationPushListener(pushes, null);

    listener.onNotificationCreated(
        new NotificationCreatedEvent(
            UUID.randomUUID(), UUID.randomUUID(), NotificationType.SYSTEM, "generic", "new notification"));

    verify(pushes).dispatch(any(), any());
  }
}
