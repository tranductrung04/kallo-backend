package com.healthcare.kallo.service.chat;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Component;

@Component
public class ChatSseService {
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1,
      Thread.ofVirtual().name("chat-sse-heartbeat-", 0).factory());

  public void execute(Runnable task) {
    Thread.ofVirtual().start(task);
  }

  public ScheduledFuture<?> scheduleHeartbeat(Runnable task, long seconds) {
    return scheduler.scheduleAtFixedRate(task, seconds, seconds, java.util.concurrent.TimeUnit.SECONDS);
  }

  @PreDestroy
  void shutdown() {
    scheduler.shutdownNow();
  }
}
