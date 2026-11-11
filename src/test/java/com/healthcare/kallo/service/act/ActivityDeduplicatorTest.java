package com.healthcare.kallo.service.act;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.kallo.model.act.ActivityLog;
import com.healthcare.kallo.model.act.ActivityType;
import com.healthcare.kallo.model.act.enums.ActivitySource;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ActivityDeduplicatorTest {
  private final ActivityDeduplicator deduplicator = new ActivityDeduplicator();

  @Test
  void treatsWalkingAndRunningAsOneGroupWhenOverlapReachesThreshold() {
    ActivityLog walking = activity("WALK_BRISK", "WALKING", ActivitySource.MANUAL,
        "2026-11-11T08:00:00Z", "2026-11-11T09:00:00Z");
    ActivityLog running = activity("RUN_8KMH", "RUNNING", ActivitySource.HEALTH_CONNECT,
        "2026-11-11T08:06:00Z", "2026-11-11T08:56:00Z");

    assertThat(deduplicator.overlaps(walking, running, 0.8)).isTrue();
    assertThat(deduplicator.winner(walking, running)).isSameAs(running);
  }

  @Test
  void doesNotDeduplicateDifferentActivityGroups() {
    ActivityLog walking = activity("WALK_BRISK", "WALKING", ActivitySource.MANUAL,
        "2026-11-11T08:00:00Z", "2026-11-11T09:00:00Z");
    ActivityLog weights = activity("WEIGHT_TRAINING", "GYM", ActivitySource.HEALTH_CONNECT,
        "2026-11-11T08:00:00Z", "2026-11-11T09:00:00Z");

    assertThat(deduplicator.overlaps(walking, weights, 0.8)).isFalse();
  }

  private ActivityLog activity(String code, String category, ActivitySource source,
      String started, String ended) {
    ActivityType type = new ActivityType();
    type.setCode(code);
    type.setCategory(category);
    ActivityLog activity = new ActivityLog();
    activity.setActivityType(type);
    activity.setSource(source);
    activity.setStartedAt(Instant.parse(started));
    activity.setEndedAt(Instant.parse(ended));
    return activity;
  }
}
