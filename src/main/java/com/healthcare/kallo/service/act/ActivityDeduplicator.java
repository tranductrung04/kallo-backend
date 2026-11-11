package com.healthcare.kallo.service.act;

import com.healthcare.kallo.model.act.ActivityLog;
import com.healthcare.kallo.model.act.enums.ActivitySource;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class ActivityDeduplicator {
  public ActivityLog winner(ActivityLog left, ActivityLog right) {
    int sourceOrder = Integer.compare(priority(left.getSource()), priority(right.getSource()));
    if (sourceOrder != 0) return sourceOrder > 0 ? left : right;
    Instant leftUpdated = Objects.requireNonNullElse(left.getUpdatedAt(), Instant.MIN);
    Instant rightUpdated = Objects.requireNonNullElse(right.getUpdatedAt(), Instant.MIN);
    return leftUpdated.compareTo(rightUpdated) >= 0 ? left : right;
  }

  public boolean overlaps(ActivityLog left, ActivityLog right, double ratio) {
    if (!sameGroup(left, right) || !left.getStartedAt().isBefore(right.getEndedAt())
        || !right.getStartedAt().isBefore(left.getEndedAt())) return false;
    Instant start = left.getStartedAt().isAfter(right.getStartedAt())
        ? left.getStartedAt() : right.getStartedAt();
    Instant end = left.getEndedAt().isBefore(right.getEndedAt()) ? left.getEndedAt() : right.getEndedAt();
    long overlap = Duration.between(start, end).toMillis();
    long shorter = Math.min(Duration.between(left.getStartedAt(), left.getEndedAt()).toMillis(),
        Duration.between(right.getStartedAt(), right.getEndedAt()).toMillis());
    return shorter > 0 && (double) overlap / shorter >= ratio;
  }

  public boolean sameGroup(ActivityLog left, ActivityLog right) {
    String leftCategory = left.getActivityType().getCategory();
    String rightCategory = right.getActivityType().getCategory();
    boolean walkingGroup = ("WALKING".equals(leftCategory) || "RUNNING".equals(leftCategory))
        && ("WALKING".equals(rightCategory) || "RUNNING".equals(rightCategory));
    return walkingGroup || (Objects.equals(leftCategory, rightCategory)
        && Objects.equals(left.getActivityType().getCode(), right.getActivityType().getCode()));
  }

  public List<ActivityLog> sorted(List<ActivityLog> activities) {
    return activities.stream().sorted(Comparator.comparing(ActivityLog::getStartedAt)).toList();
  }

  private int priority(ActivitySource source) {
    return switch (source) {
      case HEALTH_CONNECT, HEALTHKIT -> 3;
      case SENSOR, GPS -> 2;
      case MANUAL -> 1;
    };
  }
}
