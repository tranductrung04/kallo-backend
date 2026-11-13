package com.healthcare.kallo.service.sys;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketSessionRegistry {
  private static final Logger log = LoggerFactory.getLogger(WebSocketSessionRegistry.class);

  private final Object monitor = new Object();
  private final Map<UUID, Map<String, Session>> sessions = new HashMap<>();
  private final Map<String, UUID> usersBySession = new HashMap<>();
  private final Map<String, WebSocketSession> sockets = new HashMap<>();
  private final Map<String, WebSocketSession> pendingSockets = new HashMap<>();

  public boolean register(UUID userId, String sessionId, int maxSessions) {
    return register(userId, sessionId, Instant.MAX, maxSessions);
  }

  public boolean register(UUID userId, String sessionId, Instant expiresAt, int maxSessions) {
    if (userId == null || sessionId == null || expiresAt == null || maxSessions < 1) return false;
    synchronized (monitor) {
      UUID registeredUser = usersBySession.get(sessionId);
      if (registeredUser != null) return registeredUser.equals(userId);

      Map<String, Session> userSessions = sessions.computeIfAbsent(userId, ignored -> new HashMap<>());
      if (userSessions.size() >= maxSessions) return false;

      userSessions.put(sessionId, new Session(expiresAt));
      usersBySession.put(sessionId, userId);
      WebSocketSession socket = pendingSockets.remove(sessionId);
      if (socket != null) sockets.put(sessionId, socket);
      return true;
    }
  }

  public void attach(String sessionId, WebSocketSession socket) {
    if (sessionId == null || socket == null) return;
    synchronized (monitor) {
      if (usersBySession.containsKey(sessionId)) sockets.put(sessionId, socket);
      else pendingSockets.put(sessionId, socket);
    }
  }

  public void unregister(UUID userId, String sessionId) {
    if (userId == null || sessionId == null) return;
    synchronized (monitor) {
      if (!userId.equals(usersBySession.get(sessionId))) return;
      removeLocked(userId, sessionId);
    }
  }

  public void unregister(String sessionId) {
    if (sessionId == null) return;
    synchronized (monitor) {
      UUID userId = usersBySession.get(sessionId);
      if (userId != null) removeLocked(userId, sessionId);
      else pendingSockets.remove(sessionId);
    }
  }

  public void unregister(SessionDisconnectEvent event) {
    unregister(event.getSessionId());
  }

  public void expireSessions(Instant now) {
    var expired = new ArrayList<ExpiredSession>();
    synchronized (monitor) {
      for (var userEntry : sessions.entrySet()) {
        for (var sessionEntry : userEntry.getValue().entrySet()) {
          if (!sessionEntry.getValue().expiresAt().isAfter(now)) {
            expired.add(new ExpiredSession(sessionEntry.getKey(), sockets.get(sessionEntry.getKey())));
          }
        }
      }
      for (ExpiredSession session : expired) unregister(session.sessionId());
    }

    for (ExpiredSession session : expired) {
      WebSocketSession socket = session.socket();
      if (socket == null || !socket.isOpen()) continue;
      try {
        socket.close(CloseStatus.POLICY_VIOLATION);
      } catch (IOException e) {
        log.debug("Failed to close expired WebSocket session {}", session.sessionId(), e);
      }
    }
  }

  private void removeLocked(UUID userId, String sessionId) {
    Map<String, Session> userSessions = sessions.get(userId);
    if (userSessions == null) return;
    userSessions.remove(sessionId);
    usersBySession.remove(sessionId);
    sockets.remove(sessionId);
    pendingSockets.remove(sessionId);
    if (userSessions.isEmpty()) sessions.remove(userId);
  }

  private record Session(Instant expiresAt) {}

  private record ExpiredSession(String sessionId, WebSocketSession socket) {}
}
