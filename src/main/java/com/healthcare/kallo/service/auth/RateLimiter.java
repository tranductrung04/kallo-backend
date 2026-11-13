package com.healthcare.kallo.service.auth;

import java.time.Duration;

public interface RateLimiter {
  Result check(String key, int limit, Duration window);

  record Result(boolean allowed, long retryAfterSeconds) {
    public static Result permit() {
      return new Result(true, 0);
    }

    public static Result rejected(long retryAfterSeconds) {
      return new Result(false, Math.max(1, retryAfterSeconds));
    }
  }
}
