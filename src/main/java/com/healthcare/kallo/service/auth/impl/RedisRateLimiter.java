package com.healthcare.kallo.service.auth.impl;

import com.healthcare.kallo.service.auth.RateLimiter;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

@Service
public class RedisRateLimiter implements RateLimiter {
  private static final RedisScript<List> INCREMENT = RedisScript.of(
      "local count = redis.call('INCR', KEYS[1]); "
          + "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; "
          + "return {count, redis.call('TTL', KEYS[1])}", List.class);

  private final StringRedisTemplate redis;

  public RedisRateLimiter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public Result check(String key, int limit, Duration window) {
    if (limit < 1 || window == null || window.isZero() || window.isNegative()) {
      throw new IllegalArgumentException("Invalid rate limit");
    }
    List<?> result = redis.execute(INCREMENT, List.of("kallo:rate:" + key),
        Long.toString(Math.max(1, window.toSeconds())));
    long count = number(result, 0);
    long ttl = number(result, 1);
    return count <= limit ? Result.permit() : Result.rejected(ttl > 0 ? ttl : window.toSeconds());
  }

  private long number(List<?> values, int index) {
    if (values == null || values.size() <= index || !(values.get(index) instanceof Number n)) {
      throw new IllegalStateException("Redis rate limiter returned invalid result");
    }
    return n.longValue();
  }
}
