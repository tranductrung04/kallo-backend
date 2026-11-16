package com.healthcare.kallo.security.sys;

import com.healthcare.kallo.config.WebSocketProperties;
import com.healthcare.kallo.model.auth.enums.UserStatus;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.service.sys.WebSocketSessionRegistry;
import java.security.Principal;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {
  private final JwtDecoder jwtDecoder;
  private final WebSocketProperties properties;
  private final WebSocketSessionRegistry sessions;
  private final UserRepository users;

  @Autowired
  public StompAuthChannelInterceptor(
      JwtDecoder jwtDecoder, WebSocketProperties properties, WebSocketSessionRegistry sessions,
      UserRepository users) {
    this.jwtDecoder = jwtDecoder;
    this.properties = properties;
    this.sessions = sessions;
    this.users = users;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null) return message;

    StompCommand command = accessor.getCommand();
    if (command == StompCommand.CONNECT || command == StompCommand.STOMP) authenticate(accessor);
    else if (command != null && accessor.getUser() == null) {
      throw new IllegalArgumentException("STOMP frame requires authentication");
    } else if (command == StompCommand.SUBSCRIBE) authorizeSubscription(accessor);
    else if (command == StompCommand.SEND) {
      throw new IllegalArgumentException("Client STOMP SEND is not allowed");
    }
    return message;
  }

  private void authenticate(StompHeaderAccessor accessor) {
    String authorization = accessor.getFirstNativeHeader("Authorization");
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      throw new IllegalArgumentException("STOMP CONNECT requires Bearer token");
    }
    Jwt jwt;
    try {
      jwt = jwtDecoder.decode(authorization.substring(7));
    } catch (JwtException | IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid STOMP bearer token", e);
    }
    if (!Boolean.TRUE.equals(jwt.getClaim("email_verified"))) {
      throw new IllegalArgumentException("Email is not verified");
    }
    UUID userId;
    try {
      userId = UUID.fromString(jwt.getSubject());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid STOMP user id", e);
    }
    if (users != null && users.findById(userId).map(user -> user.getStatus() == UserStatus.ACTIVE).orElse(false) == false) {
      throw new IllegalArgumentException("User is disabled or does not exist");
    }
    Principal principal = () -> userId.toString();
    accessor.setUser(principal);
    if (!sessions.registerWithEviction(userId, accessor.getSessionId(), jwt.getExpiresAt(), properties.maxSessionsPerUser())) {
      throw new IllegalArgumentException("Maximum WebSocket sessions exceeded");
    }
  }

  private void authorizeSubscription(StompHeaderAccessor accessor) {
    String destination = accessor.getDestination();
    if (!Set.of("/user/queue/notifications", "/user/queue/leaderboard", "/user/queue/game-events")
        .contains(destination)) {
      throw new IllegalArgumentException("STOMP subscription destination is not allowed");
    }
  }
}
