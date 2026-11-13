package com.healthcare.kallo.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.kallo.service.auth.impl.RedisRateLimiter;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

class RedisRateLimiterTest {
  static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  static {
    REDIS.start();
  }

  @AfterAll
  static void stopRedis() {
    REDIS.stop();
  }

  @Test
  void limitAndTtlAreEnforcedByRedis() throws Exception {
    var factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
    factory.afterPropertiesSet();
    try {
      StringRedisTemplate template = new StringRedisTemplate(factory);
      template.afterPropertiesSet();
      RedisRateLimiter limiter = new RedisRateLimiter(template);
      Duration window = Duration.ofSeconds(3);

      assertThat(limiter.check("test", 2, window).allowed()).isTrue();
      assertThat(limiter.check("test", 2, window).allowed()).isTrue();
      RateLimiter.Result rejected = limiter.check("test", 2, window);
      assertThat(rejected.allowed()).isFalse();
      assertThat(rejected.retryAfterSeconds()).isBetween(1L, 3L);
      Thread.sleep(3500);
      assertThat(limiter.check("test", 2, window).allowed()).isTrue();
    } finally {
      factory.destroy();
    }
  }
}
