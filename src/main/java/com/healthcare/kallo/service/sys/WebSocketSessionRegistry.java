package com.healthcare.kallo.service.sys;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
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
  private final AtomicLong sequence = new AtomicLong();
  private final Map<UUID, Map<String, Session>> sessions = new HashMap<>();
  private final Map<String, UUID> usersBySession = new HashMap<>();
  private final Map<String, WebSocketSession> sockets = new HashMap<>();
  private final Map<String, WebSocketSession> pendingSockets = new HashMap<>();

  public boolean register(UUID userId, String sessionId, int maxSessions) {
    return register(userId, sessionId, Instant.MAX, maxSessions);
  }

  public boolean register(UUID userId, String sessionId, Instant expiresAt, int maxSessions) {
    return registerInternal(userId, sessionId, expiresAt, maxSessions, false);
  }

  public boolean registerWithEviction(UUID userId, String sessionId, Instant expiresAt, int maxSessions) {
    WebSocketSession evictedSocket = null;
    String evictedId = null;
    synchronized (monitor) {
      UUID registeredUser = usersBySession.get(sessionId);
      if (registeredUser != null) return registeredUser.equals(userId);
      Map<String, Session> userSessions = sessions.computeIfAbsent(userId, ignored -> new HashMap<>());
      if (userSessions.size() >= maxSessions) {
        evictedId = userSessions.entrySet().stream()
            .min(Map.Entry.comparingByValue(java.util.Comparator.comparingLong(Session::sequence)))
            .map(Map.Entry::getKey).orElse(null);
        if (evictedId != null) {
          evictedSocket = sockets.get(evictedId);
          removeLocked(userId, evictedId);
        }
      }
      userSessions = sessions.computeIfAbsent(userId, ignored -> new HashMap<>());
      userSessions.put(sessionId, new Session(expiresAt, sequence.incrementAndGet()));
      usersBySession.put(sessionId, userId);
      WebSocketSession socket = pendingSockets.remove(sessionId);
      if (socket != null) sockets.put(sessionId, socket);
    }
    if (evictedId != null) close(evictedId, evictedSocket);
    return true;
  }

  private boolean registerInternal(UUID userId, String sessionId, Instant expiresAt, int maxSessions,
      boolean evictOldest) {
    if (evictOldest) return registerWithEviction(userId, sessionId, expiresAt, maxSessions);
    if (userId == null || sessionId == null || expiresAt == null || maxSessions < 1) return false;
    synchronized (monitor) {
      UUID registeredUser = usersBySession.get(sessionId);
      if (registeredUser != null) return registeredUser.equals(userId);
      Map<String, Session> userSessions = sessions.computeIfAbsent(userId, ignored -> new HashMap<>());
      if (userSessions.size() >= maxSessions) return false;
      userSessions.put(sessionId, new Session(expiresAt, sequence.incrementAndGet()));
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

  private void close(String sessionId, WebSocketSession socket) {
    if (socket == null || !socket.isOpen()) return;
    try {
      socket.close(CloseStatus.POLICY_VIOLATION);
    } catch (IOException e) {
      log.debug("Failed to close evicted WebSocket session {}", sessionId, e);
    }
  }

  private record Session(Instant expiresAt, long sequence) {}

  private record ExpiredSession(String sessionId, WebSocketSession socket) {}
}
