package com.healthcare.kallo.service.act.impl;

import com.healthcare.kallo.config.ActProperties;
import com.healthcare.kallo.model.act.ActivityLog;
import com.healthcare.kallo.repository.act.ActivityLogRepository;
import com.healthcare.kallo.service.act.ActivityDeduplicator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityDeduplicatorService {
  private final ActivityLogRepository activities;
  private final ActProperties properties;
  private final ActivityDeduplicator rules = new ActivityDeduplicator();

  public ActivityDeduplicatorService(ActivityLogRepository activities, ActProperties properties) {
    this.activities = activities;
    this.properties = properties;
  }

  @Transactional
  public void deduplicate(UUID userId) {
    List<ActivityLog> active = activities.findActiveForDeduplication(userId);
    for (ActivityLog current : active) {
      ActivityLog winner = current;
      for (ActivityLog candidate : active) {
        if (candidate == current || !rules.overlaps(current, candidate,
            properties.dedupOverlapRatio().doubleValue())) continue;
        winner = rules.winner(winner, candidate);
      }
      current.setSupersededBy(winner == current ? null : winner);
      current.setUpdatedAt(Instant.now());
    }
    activities.saveAll(active);
  }
}
