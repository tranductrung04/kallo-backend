package com.healthcare.kallo.config;

import com.healthcare.kallo.service.ProfileStatusProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConditionalProfileStatusProvider {
  @Bean
  @ConditionalOnMissingBean(ProfileStatusProvider.class)
  ProfileStatusProvider profileStatusProvider() {
    return new ProfileStatusProvider() {};
  }
}
