package com.healthcare.kallo.config;

import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.repository.act.WeightLogRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.act.ProfileBodyMetrics;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConditionalActProfileBodyMetrics {
  @Bean
  @ConditionalOnMissingBean(ProfileBodyMetrics.class)
  ProfileBodyMetrics profileBodyMetrics(
      UserHealthProfileRepository healthProfiles, WeightLogRepository weightLogs) {
    return new ProfileBodyMetrics() {
      @Override
      public Optional<BigDecimal> weightKg(UUID userId) {
        return weightLogs.findTopByUserIdAndDeletedAtIsNullAndLoggedAtLessThanEqualOrderByLoggedAtDesc(userId, java.time.Instant.now())
            .map(com.healthcare.kallo.model.act.WeightLog::getWeightKg);
      }

      @Override
      public Optional<BigDecimal> heightCm(UUID userId) {
        return healthProfiles.findById(userId).map(UserHealthProfile::getHeightCm);
      }

      @Override
      public Optional<String> gender(UUID userId) {
        return healthProfiles
            .findById(userId)
            .map(profile -> profile.getGender().name().toLowerCase(Locale.ROOT));
      }
    };
  }
}
