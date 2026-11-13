package com.healthcare.kallo.config;

import com.healthcare.kallo.service.game.DailyProgressProvider;
import com.healthcare.kallo.service.game.EmptyDailyProgressProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Fallback;

@Configuration
public class ConditionalDailyProgressProvider {
  @Bean
  @Fallback
  DailyProgressProvider dailyProgressProvider() {
    return new EmptyDailyProgressProvider();
  }
}
