package com.healthcare.kallo.security.sys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.config.WebSocketProperties;
import com.healthcare.kallo.service.sys.WebSocketSessionRegistry;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class StompAuthChannelInterceptorTest {
  private final JwtDecoder decoder = mock(JwtDecoder.class);
  private final WebSocketSessionRegistry sessions = new WebSocketSessionRegistry();
  private final StompAuthChannelInterceptor interceptor =
      new StompAuthChannelInterceptor(
          decoder, new WebSocketProperties(List.of("*"), 5), sessions);

  @Test
  void connectWithoutBearerTokenIsRejected() {
    Message<?> connect = frame(StompCommand.CONNECT, "session-1", null, null, null);

    assertThatThrownBy(() -> interceptor.preSend(connect, (message, channel) -> true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("STOMP CONNECT requires Bearer token");
  }

  @Test
  void invalidOrExpiredTokenIsRejectedByJwtDecoder() {
    when(decoder.decode("expired")).thenThrow(new JwtException("expired"));
    Message<?> connect = frame(StompCommand.CONNECT, "session-1", "Bearer expired", null, null);

    assertThatThrownBy(() -> interceptor.preSend(connect, (message, channel) -> true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid STOMP bearer token");
  }

  @Test
  void unverifiedUserIsRejected() {
    UUID userId = UUID.randomUUID();
    when(decoder.decode("unverified"))
        .thenReturn(Jwt.withTokenValue("unverified")
            .subject(userId.toString())
            .claim("email_verified", false)
            .expiresAt(Instant.now().plusSeconds(60))
            .header("alg", "HS256")
            .build());

    Message<?> connect = frame(
        StompCommand.CONNECT, "session-1", "Bearer unverified", null, null);

    assertThatThrownBy(() -> interceptor.preSend(connect, (message, channel) -> true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Email is not verified");
  }

  @Test
  void verifiedConnectSetsUserAndOnlyAllowsNotificationsSubscription() {
    UUID userId = UUID.randomUUID();
    when(decoder.decode("valid")).thenReturn(validJwt(userId));

    Message<?> connected = interceptor.preSend(
        frame(StompCommand.CONNECT, "session-1", "Bearer valid", null, null),
        (message, channel) -> true);
    StompHeaderAccessor connectedHeaders = StompHeaderAccessor.wrap(connected);
    assertThat(connectedHeaders.getUser().getName()).isEqualTo(userId.toString());

    Principal principal = connectedHeaders.getUser();
    Message<?> allowed = frame(
        StompCommand.SUBSCRIBE, "session-1", null, "/user/queue/notifications", principal);
    assertThat(interceptor.preSend(allowed, (message, channel) -> true)).isSameAs(allowed);

    Message<?> unknown = frame(
        StompCommand.SUBSCRIBE, "session-1", null, "/user/queue/other", principal);
    assertThatThrownBy(() -> interceptor.preSend(unknown, (message, channel) -> true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("STOMP subscription destination is not allowed");
  }

  @Test
  void sixthVerifiedSessionIsRejected() {
    UUID userId = UUID.randomUUID();
    when(decoder.decode("valid")).thenReturn(validJwt(userId));

    for (int i = 0; i < 5; i++) {
      assertThat(interceptor.preSend(
          frame(StompCommand.CONNECT, "session-" + i, "Bearer valid", null, null),
          (message, channel) -> true)).isNotNull();
    }

    assertThatThrownBy(() -> interceptor.preSend(
        frame(StompCommand.CONNECT, "session-5", "Bearer valid", null, null),
        (message, channel) -> true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Maximum WebSocket sessions exceeded");
  }

  private static Jwt validJwt(UUID userId) {
    return Jwt.withTokenValue("valid")
        .subject(userId.toString())
        .claim("email_verified", true)
        .expiresAt(Instant.now().plusSeconds(60))
        .header("alg", "HS256")
        .build();
  }

  private static Message<byte[]> frame(
      StompCommand command,
      String sessionId,
      String authorization,
      String destination,
      Principal principal) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
    accessor.setSessionId(sessionId);
    if (authorization != null) accessor.addNativeHeader("Authorization", authorization);
    if (destination != null) accessor.setDestination(destination);
    if (principal != null) accessor.setUser(principal);
    accessor.setLeaveMutable(true);
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }
}
