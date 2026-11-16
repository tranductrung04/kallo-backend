package com.healthcare.kallo.service.sys;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class RealtimePublisher {
  private final SimpMessagingTemplate messaging;
  private final Clock clock;

  public RealtimePublisher(SimpMessagingTemplate messaging, Clock clock) {
    this.messaging = messaging;
    this.clock = clock;
  }

  public void sendToUser(UUID userId, String destination, String type, Object payload) {
    if (userId == null || destination == null || type == null) return;
    messaging.convertAndSendToUser(userId.toString(), destination,
        new RealtimeMessage(type, 1, Instant.now(clock), payload));
  }

  public record RealtimeMessage(String type, int version, Instant ts, Object payload) {}
}
