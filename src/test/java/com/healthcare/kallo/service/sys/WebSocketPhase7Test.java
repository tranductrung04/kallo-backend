package com.healthcare.kallo.service.sys;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class WebSocketPhase7Test {
  @Test
  void registryRejectsFourthSessionWithStrictRegistration() {
    var registry = new WebSocketSessionRegistry();
    UUID userId = UUID.randomUUID();
    for (int i = 0; i < 3; i++) {
      assertThat(registry.register(userId, "session-" + i, 3)).isTrue();
    }
    assertThat(registry.register(userId, "session-3", 3)).isFalse();
  }

  @Test
  void registryEvictsOldestSessionForNewConnection() {
    var registry = new WebSocketSessionRegistry();
    UUID userId = UUID.randomUUID();
    for (int i = 0; i < 3; i++) registry.register(userId, "session-" + i, 3);

    assertThat(registry.registerWithEviction(userId, "session-3", Instant.MAX, 3)).isTrue();
    assertThat(registry.register(userId, "session-0-replacement", 3)).isFalse();
  }



  @Test
  void disconnectFreesSessionSlotWithoutPrincipal() {
    var registry = new WebSocketSessionRegistry();
    UUID userId = UUID.randomUUID();
    for (int i = 0; i < 5; i++) registry.register(userId, "session-" + i, 5);

    registry.unregister("session-0");

    assertThat(registry.register(userId, "session-5", 5)).isTrue();
  }

  @Test
  void expiredSessionIsRemoved() {
    var registry = new WebSocketSessionRegistry();
    UUID userId = UUID.randomUUID();
    registry.register(userId, "session-0", Instant.parse("2026-01-01T00:00:00Z"), 1);

    registry.expireSessions(Instant.parse("2026-01-01T00:00:01Z"));

    assertThat(registry.register(userId, "session-1", 1)).isTrue();
  }
}
