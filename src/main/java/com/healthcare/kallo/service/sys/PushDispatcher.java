package com.healthcare.kallo.service.sys;

import java.util.List;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import com.healthcare.kallo.repository.sys.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class PushDispatcher {
  private final DeviceService devices;
  private final PushSender sender;
  private final NotificationRepository notifications;
  private final Clock clock;
  private static final Logger log = LoggerFactory.getLogger(PushDispatcher.class);

  @Autowired
  public PushDispatcher(
      DeviceService devices, PushSender sender, NotificationRepository notifications, Clock clock) {
    this.devices = devices;
    this.sender = sender;
    this.notifications = notifications;
    this.clock = clock;
  }

  public PushDispatcher(DeviceService devices, PushSender sender) {
    this(devices, sender, null, Clock.systemUTC());
  }

  @Async("pushTaskExecutor")
  @Transactional
  public void dispatch(UUID userId, PushMessage message) {
    try {
      List<String> tokens = devices.tokensForUser(userId);
      List<PushResult> results = sender.send(message, tokens);
      results.stream()
          .filter(result -> result.status() == PushResult.Status.INVALID_TOKEN)
          .forEach(result -> devices.removeByToken(result.token()));
      if (notifications != null
          && results.stream().anyMatch(result -> result.status() == PushResult.Status.SENT)) {
        notifications.markPushed(message.notificationIdAsUuid(), Instant.now(clock));
      }
    } catch (RuntimeException e) {
      log.error("Failed to dispatch push for notification {}", message.notificationId(), e);
    }
  }
}
