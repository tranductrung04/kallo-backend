package com.healthcare.kallo.config;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
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
    executor.setTaskDecorator(mdcTaskDecorator());
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
    executor.setTaskDecorator(mdcTaskDecorator());
    executor.setRejectedExecutionHandler(
        (task, pool) ->
            LoggerFactory.getLogger("kallo-push-executor")
                .warn("Push task rejected because the executor queue is full"));
    executor.initialize();
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

  @Bean
  TaskDecorator mdcTaskDecorator() {
    return runnable -> {
      Map<String, String> context = MDC.getCopyOfContextMap();
      String requestId = context == null ? null : context.get("request_id");
      if (requestId == null || requestId.isBlank()) {
        requestId = "async-" + UUID.randomUUID();
        context = context == null ? new java.util.HashMap<>() : new java.util.HashMap<>(context);
        context.put("request_id", requestId);
      }
      Map<String, String> captured = context;
      return () -> {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
          MDC.setContextMap(captured);
          runnable.run();
        } finally {
          if (previous == null) MDC.clear(); else MDC.setContextMap(previous);
        }
      };
    };
  }
}
