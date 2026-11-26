package com.healthcare.kallo.service.nutri;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.NutriProperties;
import com.healthcare.kallo.service.auth.RateLimiter;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NutriRateLimiter {

  private static final Logger log = LoggerFactory.getLogger(NutriRateLimiter.class);

  private final RateLimiter rateLimiter;
  private final NutriProperties properties;

  public NutriRateLimiter(RateLimiter rateLimiter, NutriProperties properties) {
    this.rateLimiter = rateLimiter;
    this.properties = properties;
  }

  public void checkRecognize(UUID userId) {
    int limit = properties.getRateLimit().getRecognizePerMinute();
    try {
      RateLimiter.Result result = rateLimiter.check(
          "nutri:recognize:" + userId, limit, Duration.ofMinutes(1));
      if (!result.allowed()) {
        throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, result.retryAfterSeconds());
      }
    } catch (BusinessException e) {
      throw e;
    } catch (RuntimeException e) {
      log.warn("NUTRI rate limiter unavailable, failing open for user {}", userId, e);
    }
  }
}
