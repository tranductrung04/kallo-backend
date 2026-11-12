package com.healthcare.kallo.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.slf4j.LoggerFactory;

@Configuration
@EnableAsync
public class AsyncConfig {
  @Bean(name = "mailTaskExecutor")
  Executor mailTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("kallo-mail-");
    executor.initialize();
    return executor;
  }

  @Bean(name = "pushTaskExecutor")
  Executor pushTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(200);
    executor.setThreadNamePrefix("kallo-push-");
    executor.setRejectedExecutionHandler(
        (task, pool) ->
            LoggerFactory.getLogger("kallo-push-executor")
                .warn("Push task rejected because the executor queue is full"));
    executor.initialize();
    return executor;
  }
}
