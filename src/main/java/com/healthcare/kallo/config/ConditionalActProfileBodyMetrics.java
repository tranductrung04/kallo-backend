package com.healthcare.kallo.config;

import com.healthcare.kallo.service.act.ProfileBodyMetrics;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConditionalActProfileBodyMetrics {
  @Bean
  @ConditionalOnMissingBean(ProfileBodyMetrics.class)
  ProfileBodyMetrics profileBodyMetrics() {
    return new ProfileBodyMetrics() {
      @Override
      public Optional<BigDecimal> weightKg(UUID userId) {
        return Optional.empty();
      }

      @Override
      public Optional<BigDecimal> heightCm(UUID userId) {
        return Optional.empty();
      }
    };
  }
}
