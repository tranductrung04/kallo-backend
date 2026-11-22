package com.healthcare.kallo.config;

import com.healthcare.kallo.service.act.ProfileBodyMetrics;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.act.WeightLogRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConditionalActProfileBodyMetrics {
  @Bean
  @ConditionalOnMissingBean(ProfileBodyMetrics.class)
  ProfileBodyMetrics profileBodyMetrics(UserRepository users, WeightLogRepository weightLogs) {
    return new ProfileBodyMetrics() {
      @Override
      public Optional<BigDecimal> weightKg(UUID userId) {
        return weightLogs.findTopByUserIdAndDeletedAtIsNullAndLoggedAtLessThanEqualOrderByLoggedAtDesc(userId, java.time.Instant.now())
            .map(com.healthcare.kallo.model.act.WeightLog::getWeightKg);
      }

      @Override
      public Optional<BigDecimal> heightCm(UUID userId) {
        return users.findById(userId).map(com.healthcare.kallo.model.auth.User::getHeightCm);
      }
    };
  }
}
