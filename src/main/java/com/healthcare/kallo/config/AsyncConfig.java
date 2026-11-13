package com.healthcare.kallo.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {
  private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

  @Bean(name = "mailTaskExecutor")
  ThreadPoolTaskExecutor mailTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("kallo-mail-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    return executor;
  }

  @Bean(name = "gameTaskExecutor")
  ThreadPoolTaskExecutor gameTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(200);
    executor.setThreadNamePrefix("kallo-game-");
    executor.setRejectedExecutionHandler(
        new ThreadPoolExecutor.DiscardPolicy() {
          @Override
          public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
            log.warn("GAME async task rejected because executor queue is full");
          }
        });
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    return executor;
  }
}
