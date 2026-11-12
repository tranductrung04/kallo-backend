package com.healthcare.kallo.security.sys;

import com.healthcare.kallo.config.WebSocketProperties;
import com.healthcare.kallo.service.sys.WebSocketSessionRegistry;
import java.security.Principal;
import java.util.UUID;
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

  public StompAuthChannelInterceptor(
      JwtDecoder jwtDecoder, WebSocketProperties properties, WebSocketSessionRegistry sessions) {
    this.jwtDecoder = jwtDecoder;
    this.properties = properties;
    this.sessions = sessions;
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
    Principal principal = () -> userId.toString();
    accessor.setUser(principal);
    if (!sessions.register(userId, accessor.getSessionId(), jwt.getExpiresAt(), properties.maxSessionsPerUser())) {
      throw new IllegalArgumentException("Maximum WebSocket sessions exceeded");
    }
  }

  private void authorizeSubscription(StompHeaderAccessor accessor) {
    String destination = accessor.getDestination();
    if (destination == null || !destination.startsWith("/user/queue/")) {
      throw new IllegalArgumentException("STOMP subscription destination is not allowed");
    }
  }
}
