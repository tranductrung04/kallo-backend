package com.healthcare.kallo.service.chat;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.ChatProperties;
import com.healthcare.kallo.service.auth.RateLimiter;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ChatRateLimiter {
  private final RateLimiter limiter;
  private final ChatProperties properties;
  private final ChatMetrics metrics;

  public ChatRateLimiter(RateLimiter limiter, ChatProperties properties, ChatMetrics metrics) {
    this.limiter = limiter;
    this.properties = properties;
    this.metrics = metrics;
  }

  public void check(UUID userId) {
    var minute = limiter.check("chat:" + userId + ":minute", properties.rateLimit().perMinute(), Duration.ofMinutes(1));
    if (!minute.allowed()) {
      metrics.recordRateLimitHit();
      throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, minute.retryAfterSeconds());
    }
    var day = limiter.check("chat:" + userId + ":day", properties.rateLimit().perDay(), Duration.ofDays(1));
    if (!day.allowed()) {
      metrics.recordRateLimitHit();
      throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, day.retryAfterSeconds());
    }
  }
}
