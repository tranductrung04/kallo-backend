package com.healthcare.kallo.service.auth;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class MailMetrics {
  private final MeterRegistry registry;

  public MailMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public void recordSent(String type) {
    counter("mail.sent", type).increment();
  }

  public void recordFailed(String type) {
    counter("mail.failed", type).increment();
  }

  public void recordRejected() {
    registry.counter("mail.rejected").increment();
  }

  private io.micrometer.core.instrument.Counter counter(String name, String type) {
    return registry.counter(name, "type", type);
  }
}
