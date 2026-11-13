package com.healthcare.kallo.config;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.healthcare.kallo.service.sys.UserActivityPort;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class SysActivityConfigTest {
  @Test
  void applicationImplementationWinsOverNoopFallback() {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.register(SysActivityConfig.class, RealActivityConfig.class);
      context.refresh();

      assertSame(RealActivityConfig.REAL_PORT, context.getBean(UserActivityPort.class));
    }
  }

  @Configuration
  static class RealActivityConfig {
    static final UserActivityPort REAL_PORT = new UserActivityPort() {
      public java.util.Optional<java.time.Instant> lastWaterLogAt(java.util.UUID userId) {
        return java.util.Optional.empty();
      }

      public boolean hasMealLog(
          java.util.UUID userId, java.time.LocalDate date, String mealType) {
        return true;
      }

      public java.util.List<java.time.LocalTime> recentMealLogTimes(
          java.util.UUID userId, String mealType, int days) {
        return java.util.List.of();
      }
    };

    @Bean
    UserActivityPort realUserActivityPort() {
      return REAL_PORT;
    }
  }
}
