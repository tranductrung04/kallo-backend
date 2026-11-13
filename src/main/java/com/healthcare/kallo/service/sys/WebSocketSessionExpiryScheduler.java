package com.healthcare.kallo.service.sys;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WebSocketSessionExpiryScheduler {
  private final WebSocketSessionRegistry sessions;
  private final Clock clock;

  public WebSocketSessionExpiryScheduler(WebSocketSessionRegistry sessions, Clock clock) {
    this.sessions = sessions;
    this.clock = clock;
  }

  @Scheduled(fixedDelay = 1_000)
  public void closeExpiredSessions() {
    Map<String, String> previous = MDC.getCopyOfContextMap();
    MDC.put("request_id", "async-" + UUID.randomUUID());
    try {
      sessions.expireSessions(Instant.now(clock));
    } finally {
      if (previous == null) MDC.clear();
      else MDC.setContextMap(previous);
    }
  }
}
