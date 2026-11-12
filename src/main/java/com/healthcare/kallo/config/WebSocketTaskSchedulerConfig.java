package com.healthcare.kallo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class WebSocketTaskSchedulerConfig {
  @Bean
  ThreadPoolTaskScheduler brokerTaskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("kallo-ws-");
    scheduler.initialize();
    return scheduler;
  }
}
