package com.healthcare.kallo.filter;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.GameProperties;
import com.healthcare.kallo.config.RateLimitProperties;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.auth.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Component
@Order(-99)
public class RateLimitFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
  private static final long UNAVAILABLE_LOG_INTERVAL_MILLIS = 30_000L;
  private final AtomicLong lastUnavailableLog = new AtomicLong();
  private final RateLimiter limiter;

  private boolean shouldLogUnavailable() {
    long now = System.currentTimeMillis();
    long previous = lastUnavailableLog.get();
    return now - previous >= UNAVAILABLE_LOG_INTERVAL_MILLIS
        && lastUnavailableLog.compareAndSet(previous, now);
  }

  private void logUnavailable(String prefix, RuntimeException exception) {
    if (shouldLogUnavailable()) {
      log.warn("Rate limiter unavailable; allowing request for {}", prefix, exception);
    }
  }

  private final RateLimitProperties properties;
  private final GameProperties gameProperties;
  private final ObjectMapper objectMapper;

  @Autowired
  public RateLimitFilter(RateLimiter limiter, RateLimitProperties properties, GameProperties gameProperties,
      ObjectMapper objectMapper) {
    this.limiter = limiter;
    this.properties = properties;
    this.gameProperties = gameProperties;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Limit limit = limit(request);
    if (limit == null) {
      chain.doFilter(request, response);
      return;
    }
    String identity = userId();
    if (identity == null) {
      chain.doFilter(request, response);
      return;
    }
    RateLimiter.Result result;
    try {
      result = limiter.check(limit.prefix + ":" + identity, limit.max, limit.window);
    } catch (RuntimeException e) {
      logUnavailable(limit.prefix, e);
      chain.doFilter(request, response);
      return;
    }
    if (!result.allowed()) {
      response.setStatus(ErrorCode.TOO_MANY_REQUESTS.status().value());
      response.setHeader("Retry-After", Long.toString(result.retryAfterSeconds()));
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.getWriter().write(objectMapper.writeValueAsString(
          ApiResponse.error(ErrorCode.TOO_MANY_REQUESTS.name(), ErrorCode.TOO_MANY_REQUESTS.message())));
      return;
    }
    chain.doFilter(request, response);
  }

  private String userId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) return null;
    try {
      return CurrentUser.id(authentication).toString();
    } catch (RuntimeException e) {
      log.debug("Could not resolve authenticated user for rate limiting", e);
      return null;
    }
  }

  private Limit limit(HttpServletRequest request) {
    String path = request.getServletPath();
    if (path == null || path.isBlank()) {
      String contextPath = request.getContextPath();
      path = request.getRequestURI().substring(contextPath.length());
    }
    String method = request.getMethod();
    if ("POST".equals(method) && "/api/devices".equals(path)) {
      return new Limit("devices", properties.devicesPerHour(), properties.hour());
    }
    if ("POST".equals(method) && "/api/sync/push".equals(path)) {
      return new Limit("sync-push", properties.syncPushPerMinute(), properties.minute());
    }
    if ("GET".equals(method) && "/api/sync/pull".equals(path)) {
      return new Limit("sync-pull", properties.syncPullPerMinute(), properties.minute());
    }
    if ("GET".equals(method) && "/api/notifications".equals(path)) {
      return new Limit("notifications", properties.notificationsPerMinute(), properties.minute());
    }
    if ("PUT".equals(method) && "/api/notification-settings".equals(path)) {
      return new Limit("notification-settings", properties.settingsPerMinute(), properties.minute());
    }
    if ("POST".equals(method) && "/api/friends/requests".equals(path) && gameProperties != null) {
      return new Limit("friend-requests", gameProperties.friend().requestsPerHour(), gameProperties.friend().requestWindow());
    }
    return null;
  }

  private record Limit(String prefix, int max, java.time.Duration window) {}
}
