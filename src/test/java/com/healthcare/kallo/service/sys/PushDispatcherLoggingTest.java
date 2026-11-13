package com.healthcare.kallo.service.sys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import com.healthcare.kallo.repository.sys.NotificationRepository;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class PushDispatcherLoggingTest {
  private final Logger logger = (Logger) LoggerFactory.getLogger(PushDispatcher.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  @BeforeEach
  void attachAppender() {
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    logger.detachAppender(appender);
    MDC.clear();
  }

  @Test
  void logsAggregatePushResultsWithoutPayloadOrFullTokens() {
    DeviceService devices = mock(DeviceService.class);
    PushSender sender = mock(PushSender.class);
    UUID userId = UUID.randomUUID();
    String notificationId = UUID.randomUUID().toString();
    String token = "secret-full-device-token";
    when(devices.tokensForUser(userId)).thenReturn(List.of(token, "bad", "retry", "failed"));
    when(sender.send(any(), eq(List.of(token, "bad", "retry", "failed"))))
        .thenReturn(List.of(
            new PushResult(token, PushResult.Status.SENT),
            new PushResult("bad", PushResult.Status.INVALID_TOKEN),
            new PushResult("retry", PushResult.Status.RETRYABLE_FAILURE),
            new PushResult("failed", PushResult.Status.FAILURE)));
    MDC.put("request_id", "req-42");

    new PushDispatcher(devices, sender, mock(NotificationRepository.class), Clock.systemUTC())
        .dispatch(userId, new PushMessage("CALORIE_OVER", notificationId, "private title", "private body"));

    assertThat(appender.list).hasSize(1);
    String message = appender.list.getFirst().getFormattedMessage();
    assertThat(message).contains(
        "notificationId=" + notificationId,
        "userId=" + userId,
        "type=CALORIE_OVER",
        "sent=1",
        "invalid=1",
        "retryable=1",
        "failure=1",
        "request_id=req-42");
    assertThat(message).doesNotContain(token, "private title", "private body");
  }
}
