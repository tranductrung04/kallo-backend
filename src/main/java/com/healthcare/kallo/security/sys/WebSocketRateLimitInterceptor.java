package com.healthcare.kallo.security.sys;

import com.healthcare.kallo.config.RateLimitProperties;
import com.healthcare.kallo.service.auth.RateLimiter;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.stereotype.Component;

@Component
public class WebSocketRateLimitInterceptor implements HandshakeInterceptor {
  private static final Logger log = LoggerFactory.getLogger(WebSocketRateLimitInterceptor.class);
  private static final long UNAVAILABLE_LOG_INTERVAL_MILLIS = 30_000L;
  private final AtomicLong lastUnavailableLog = new AtomicLong();
  private final RateLimiter limiter;

  private void logUnavailable(RuntimeException exception) {
    long now = System.currentTimeMillis();
    long previous = lastUnavailableLog.get();
    if (now - previous >= UNAVAILABLE_LOG_INTERVAL_MILLIS
        && lastUnavailableLog.compareAndSet(previous, now)) {
      log.warn("WebSocket rate limiter unavailable; allowing handshake", exception);
    }
  }

  private final RateLimitProperties properties;

  public WebSocketRateLimitInterceptor(RateLimiter limiter, RateLimitProperties properties) {
    this.limiter = limiter;
    this.properties = properties;
  }

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler handler,
      Map<String, Object> attributes) {
    InetSocketAddress remote = request.getRemoteAddress();
    String ip = remote == null || remote.getAddress() == null
        ? "unknown"
        : remote.getAddress().getHostAddress();
    RateLimiter.Result result;
    try {
      result = limiter.check(
          "websocket-ip:" + ip,
          properties.websocketPerMinute(),
          properties.minute());
    } catch (RuntimeException e) {
      logUnavailable(e);
      return true;
    }
    if (result.allowed()) return true;
    response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
    response.getHeaders().set("Retry-After", Long.toString(result.retryAfterSeconds()));
    return false;
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler handler,
      Exception exception) {}
}
