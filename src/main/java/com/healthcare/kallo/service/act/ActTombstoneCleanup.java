package com.healthcare.kallo.service.act;

import com.healthcare.kallo.config.ActProperties;
import com.healthcare.kallo.repository.act.ActivityLogRepository;
import com.healthcare.kallo.repository.act.WeightLogRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ActTombstoneCleanup {
  private final WeightLogRepository weightLogs;
  private final ActivityLogRepository activities;
  private final ActProperties properties;
  private final Clock clock;

  public ActTombstoneCleanup(
      WeightLogRepository weightLogs,
      ActivityLogRepository activities,
      ActProperties properties,
      Clock clock) {
    this.weightLogs = weightLogs;
    this.activities = activities;
    this.properties = properties;
    this.clock = clock;
  }

  @Scheduled(cron = "${app.act.tombstone-cleanup-cron:0 0 3 * * *}", zone = "UTC")
  @Transactional
  public void deleteExpiredTombstones() {
    Instant cutoff = clock.instant().minus(properties.tombstoneRetention());
    weightLogs.deleteTombstonesBefore(cutoff);
    activities.deleteTombstonesBefore(cutoff);
  }
}
