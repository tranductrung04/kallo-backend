package com.healthcare.kallo.service.sys;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import com.healthcare.kallo.repository.sys.NotificationRepository;
import org.slf4j.MDC;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  @Async("pushTaskExecutor")
  public void dispatch(UUID userId, PushMessage message) {
    Map<String, String> previous = MDC.getCopyOfContextMap();
    MDC.put("request_id", requestId());
    try {
      dispatchNow(userId, message);
    } finally {
      if (previous == null) MDC.clear();
      else MDC.setContextMap(previous);
    }
  }

  private void dispatchNow(UUID userId, PushMessage message) {
    long started = System.nanoTime();
    try {
      List<String> tokens = devices.tokensForUser(userId);
      List<PushResult> results = sender.send(message, tokens);
      long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
      Map<PushResult.Status, Long> counts = new java.util.EnumMap<>(PushResult.Status.class);
      results.forEach(result -> counts.merge(result.status(), 1L, Long::sum));
      log.info(
          "Push dispatch notificationId={} userId={} type={} sent={} invalid={} retryable={} failure={} latencyMs={} request_id={}",
          message.notificationId(), userId, message.type(),
          counts.getOrDefault(PushResult.Status.SENT, 0L),
          counts.getOrDefault(PushResult.Status.INVALID_TOKEN, 0L),
          counts.getOrDefault(PushResult.Status.RETRYABLE_FAILURE, 0L),
          counts.getOrDefault(PushResult.Status.FAILURE, 0L), latencyMs, requestId());
      results.stream()
          .filter(result -> result.status() == PushResult.Status.INVALID_TOKEN)
          .forEach(result -> devices.removeByToken(result.token()));
      if (results.stream().anyMatch(result -> result.status() == PushResult.Status.SENT)) {
        notifications.markPushed(message.notificationIdAsUuid(), Instant.now(clock));
      }
    } catch (RuntimeException e) {
      long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
      log.error(
          "Push dispatch notificationId={} userId={} type={} result=FAILURE latencyMs={} request_id={}",
          message.notificationId(), userId, message.type(), latencyMs, requestId(), e);
    }
  }

  private String requestId() {
    String value = MDC.get("request_id");
    return value == null || value.isBlank() ? "async-" + UUID.randomUUID() : value;
  }
}
