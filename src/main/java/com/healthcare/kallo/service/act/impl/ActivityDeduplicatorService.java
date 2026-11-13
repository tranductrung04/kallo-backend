package com.healthcare.kallo.service.act.impl;

import com.healthcare.kallo.config.ActProperties;
import com.healthcare.kallo.model.act.ActivityLog;
import com.healthcare.kallo.repository.act.ActivityLogRepository;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.service.act.ActivityDeduplicator;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityDeduplicatorService {
  private static final Duration WINDOW_PADDING = Duration.ofHours(24);

  private final ActivityLogRepository activities;
  private final UserRepository users;
  private final ActProperties properties;
  private final ActivityDeduplicator rules = new ActivityDeduplicator();

  public ActivityDeduplicatorService(ActivityLogRepository activities, UserRepository users,
      ActProperties properties) {
    this.activities = activities;
    this.users = users;
    this.properties = properties;
  }

  @Transactional
  public void deduplicate(UUID userId, Instant from, Instant to) {
    users.findByIdForUpdate(userId).orElseThrow();
    Instant windowStart = from.minus(WINDOW_PADDING);
    Instant windowEnd = to.plus(WINDOW_PADDING);
    List<ActivityLog> candidates = activities.findForDeduplication(userId, windowStart, windowEnd);
    Map<UUID, UUID> winners = rules.deduplicate(candidates,
        properties.dedupOverlapRatio().doubleValue());
    for (ActivityLog candidate : candidates) {
      UUID winnerId = winners.get(candidate.getId());
      UUID currentId = candidate.getSupersededBy() == null ? null : candidate.getSupersededBy().getId();
      if (!Objects.equals(currentId, winnerId)) {
        activities.updateSupersededBy(userId, candidate.getId(), winnerId);
      }
    }
  }
}
