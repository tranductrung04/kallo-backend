package com.healthcare.kallo.service.sys;

import java.time.Clock;
import java.time.Instant;
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
    sessions.expireSessions(Instant.now(clock));
  }
}
