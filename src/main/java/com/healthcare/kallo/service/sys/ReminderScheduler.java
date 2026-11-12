package com.healthcare.kallo.service.sys;

import java.time.Clock;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.reminder.enabled", havingValue = "true", matchIfMissing = true)
public class ReminderScheduler {
  private final ReminderEngine engine;
  private final Clock clock;

  public ReminderScheduler(ReminderEngine engine, Clock clock) {
    this.engine = engine;
    this.clock = clock;
  }

  @Scheduled(fixedDelay = 60_000)
  public void run() {
    engine.run(Instant.now(clock));
  }
}
