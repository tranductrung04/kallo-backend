package com.healthcare.kallo.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class WebSocketTaskSchedulerConfig {
  @Bean(name = "brokerTaskScheduler")
  @Qualifier("brokerTaskScheduler")
  ThreadPoolTaskScheduler brokerTaskScheduler(TaskDecorator mdcTaskDecorator) {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("kallo-ws-");
    scheduler.setTaskDecorator(mdcTaskDecorator);
    scheduler.initialize();
    return scheduler;
  }

  @Bean(name = "taskScheduler")
  ThreadPoolTaskScheduler taskScheduler(TaskDecorator mdcTaskDecorator) {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(4);
    scheduler.setThreadNamePrefix("kallo-scheduled-");
    scheduler.setTaskDecorator(mdcTaskDecorator);
    scheduler.initialize();
    return scheduler;
  }
}
