package com.healthcare.kallo.security.sys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.healthcare.kallo.config.RateLimitProperties;
import com.healthcare.kallo.service.auth.RateLimiter;
import java.net.InetSocketAddress;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

class WebSocketRateLimitInterceptorTest {
  @Test
  void rejectedHandshakeReturns429AndRetryAfter() {
    RateLimiter limiter = (key, limit, window) -> RateLimiter.Result.rejected(12);
    var interceptor = new WebSocketRateLimitInterceptor(
        limiter, new RateLimitProperties(20, 30, 60, 120, 30, 10));
    ServerHttpRequest request = mock(ServerHttpRequest.class);
    ServerHttpResponse response = mock(ServerHttpResponse.class);
    when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("192.0.2.10", 1234));
    HttpHeaders headers = new HttpHeaders();
    when(response.getHeaders()).thenReturn(headers);

    boolean allowed = interceptor.beforeHandshake(request, response, (WebSocketHandler) null, Map.of());

    assertThat(allowed).isFalse();
    verify(response).setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(headers.getFirst("Retry-After")).isEqualTo("12");
  }

  @Test
  void limiterFailureFailsOpen() {
    RateLimiter limiter = (key, limit, window) -> { throw new IllegalStateException("redis down"); };
    var interceptor = new WebSocketRateLimitInterceptor(
        limiter, new RateLimitProperties(20, 30, 60, 120, 30, 10));
    ServerHttpRequest request = mock(ServerHttpRequest.class);
    ServerHttpResponse response = mock(ServerHttpResponse.class);

    assertThat(interceptor.beforeHandshake(request, response, (WebSocketHandler) null, Map.of())).isTrue();
  }
}
