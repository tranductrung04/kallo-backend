package com.healthcare.kallo.service.chat;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class ChatSseService {
  private final ThreadPoolExecutor workers = new ThreadPoolExecutor(0, 32, 60, TimeUnit.SECONDS,
      new java.util.concurrent.SynchronousQueue<>(), Thread.ofVirtual().name("chat-sse-worker-", 0).factory(),
      new ThreadPoolExecutor.AbortPolicy());
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2,
      Thread.ofVirtual().name("chat-sse-heartbeat-", 0).factory());

  public void execute(Runnable task) {
    var context = MDC.getCopyOfContextMap();
    try {
      workers.execute(() -> {
        var previous = MDC.getCopyOfContextMap();
        try {
          if (context == null) MDC.clear(); else MDC.setContextMap(context);
          task.run();
        } finally {
          if (previous == null) MDC.clear(); else MDC.setContextMap(previous);
        }
      });
    } catch (java.util.concurrent.RejectedExecutionException exception) {
      throw new ChatSseException("CHAT_STREAM_CAPACITY", "Too many active chat streams");
    }
  }

  public ScheduledFuture<?> scheduleHeartbeat(Runnable task, long seconds) {
    return scheduler.scheduleAtFixedRate(task, seconds, seconds, java.util.concurrent.TimeUnit.SECONDS);
  }

  @PreDestroy
  void shutdown() {
    workers.shutdownNow();
    scheduler.shutdownNow();
  }
}
