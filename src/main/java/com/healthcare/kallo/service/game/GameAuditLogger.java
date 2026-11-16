package com.healthcare.kallo.service.game;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class GameAuditLogger {
  private static final Logger log = LoggerFactory.getLogger("GAME_AUDIT");

  public void friend(String action, UUID userId, UUID targetId) {
    write(action, userId, targetId, null);
  }

  public void badge(String action, UUID userId, UUID badgeId) {
    write(action, userId, badgeId, null);
  }

  public void challenge(String action, UUID userId, UUID challengeId) {
    write(action, userId, challengeId, null);
  }

  public void websocket(String action, UUID userId, String sessionId, String reason) {
    log.atInfo().addKeyValue("event", action).addKeyValue("userId", userId)
        .addKeyValue("sessionId", sessionId).addKeyValue("reason", reason)
        .addKeyValue("request_id", MDC.get("request_id")).log("GAME audit event");
  }

  private void write(String action, UUID userId, UUID targetId, String result) {
    log.atInfo().addKeyValue("event", action).addKeyValue("userId", userId)
        .addKeyValue("targetId", targetId).addKeyValue("result", result)
        .addKeyValue("request_id", MDC.get("request_id")).log("GAME audit event");
  }
}
