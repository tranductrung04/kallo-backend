package com.healthcare.kallo.service.chat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ChatMetrics {
  private static final Logger log = LoggerFactory.getLogger(ChatMetrics.class);
  private final MeterRegistry registry;
  private final AtomicInteger activeSseConnections = new AtomicInteger();

  public ChatMetrics(MeterRegistry registry) {
    this.registry = registry;
    registry.gauge("chat.sse.active.connections", activeSseConnections);
  }

  public Timer.Sample startAiTimer() {
    return Timer.start(registry);
  }

  public void recordAiLatency(Timer.Sample sample) {
    Timer timer = Timer.builder("chat.ai.latency")
        .publishPercentileHistogram()
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(registry);
    sample.stop(timer);
  }

  public void recordMessage(String status) {
    counter("chat.messages.sent", "status", status).increment();
  }

  public void recordAiError(String type) {
    counter("chat.ai.errors", "type", type).increment();
  }

  public void recordRateLimitHit() {
    registry.counter("chat.ratelimit.hits").increment();
  }

  public void recordSafetyBlocked(String reason) {
    counter("chat.safety.blocked", "reason", reason).increment();
  }

  public void recordAllergenHit() {
    registry.counter("chat.safety.allergen.hits").increment();
  }

  public void recordFeedback(String value) {
    counter("chat.feedback", "value", value).increment();
  }

  public void sseOpened() {
    activeSseConnections.incrementAndGet();
  }

  public void sseClosed() {
    int current = activeSseConnections.decrementAndGet();
    if (current < 0) {
      activeSseConnections.incrementAndGet();
      log.warn("SSE connection close without matching open");
    }
  }

  private Counter counter(String name, String tag, String value) {
    return registry.counter(name, tag, value == null || value.isBlank() ? "unknown" : value);
  }
}
