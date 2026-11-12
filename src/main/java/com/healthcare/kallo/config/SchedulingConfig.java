package com.healthcare.kallo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;
import org.springframework.context.annotation.Bean;

@Configuration
@EnableScheduling
public class SchedulingConfig {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }}
