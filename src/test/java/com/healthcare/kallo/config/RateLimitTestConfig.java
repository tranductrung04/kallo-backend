package com.healthcare.kallo.config;

import com.healthcare.kallo.service.auth.RateLimiter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class RateLimitTestConfig {
  @Bean
  RateLimitProperties rateLimitProperties() {
    return new RateLimitProperties(20, 30, 60, 120, 30, 10);
  }

  @Bean
  RateLimiter rateLimiter() {
    return (key, limit, window) -> RateLimiter.Result.permit();
  }
}
