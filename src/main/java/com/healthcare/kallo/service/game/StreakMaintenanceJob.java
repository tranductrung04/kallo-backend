package com.healthcare.kallo.service.game;

import com.healthcare.kallo.repository.game.StreakRepository;
import java.util.ArrayList;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StreakMaintenanceJob {
  private static final Logger log = LoggerFactory.getLogger(StreakMaintenanceJob.class);
  private static final int BATCH_SIZE = 500;
  private final StreakRepository streaks;
  private final StreakService service;

  public StreakMaintenanceJob(StreakRepository streaks, StreakService service) {
    this.streaks = streaks;
    this.service = service;
  }

  @Scheduled(cron = "0 0 * * * *", zone = "UTC")
  public void resetExpiredStreaks() {
    try {
      streaks.resetExpiredCurrentStreaks();
    } catch (RuntimeException exception) {
      log.error("GAME hourly streak reset failed", exception);
    }
  }

  @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Ho_Chi_Minh")
  public void recomputeActiveStreaks() {
    var ids = new ArrayList<UUID>();
    var page = streaks.findByCurrentStreakGreaterThan(0, org.springframework.data.domain.PageRequest.of(0, BATCH_SIZE));
    do {
      page.getContent().stream().map(com.healthcare.kallo.model.game.Streak::getUserId).forEach(ids::add);
      if (!page.hasNext()) break;
      page = streaks.findByCurrentStreakGreaterThan(0, page.nextPageable());
    } while (true);
    ids.forEach(id -> {
      try {
        service.recompute(id, null);
      } catch (RuntimeException exception) {
        log.warn("GAME nightly streak recompute failed for user {}", id, exception);
      }
    });
  }
}
