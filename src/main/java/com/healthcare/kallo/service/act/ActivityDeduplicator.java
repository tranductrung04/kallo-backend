package com.healthcare.kallo.service.act;

import com.healthcare.kallo.model.act.ActivityLog;
import com.healthcare.kallo.model.act.enums.ActivitySource;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ActivityDeduplicator {
  public ActivityLog winner(ActivityLog left, ActivityLog right) {
    return comparator().compare(left, right) <= 0 ? left : right;
  }

  public Comparator<ActivityLog> comparator() {
    return (left, right) -> {
    int sourceOrder = Integer.compare(priority(left.getSource()), priority(right.getSource()));
    if (sourceOrder != 0) return -sourceOrder;
    Instant leftUpdated = Objects.requireNonNullElse(left.getUpdatedAt(), Instant.MIN);
    Instant rightUpdated = Objects.requireNonNullElse(right.getUpdatedAt(), Instant.MIN);
    int updated = rightUpdated.compareTo(leftUpdated);
    if (updated != 0) return updated;
    int created = compareDesc(left.getCreatedAt(), right.getCreatedAt());
    if (created != 0) return created;
    int id = compareDesc(left.getId(), right.getId());
    if (id != 0) return id;
    String leftKey = tieKey(left);
    String rightKey = tieKey(right);
    return leftKey.compareTo(rightKey);
    };
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

  public Map<UUID, UUID> deduplicate(List<ActivityLog> activities, double overlapRatio) {
    List<ActivityLog> ordered = new ArrayList<>(activities);
    ordered.sort(comparator());
    List<ActivityLog> kept = new ArrayList<>();
    Map<UUID, UUID> winners = new HashMap<>();
    for (ActivityLog candidate : ordered) {
      ActivityLog winner = kept.stream()
          .filter(keptActivity -> overlaps(candidate, keptActivity, overlapRatio))
          .min(comparator())
          .orElse(null);
      winners.put(candidate.getId(), winner == null ? null : winner.getId());
      if (winner == null) kept.add(candidate);
    }
    return winners;
  }

  private int priority(ActivitySource source) {
    return switch (source) {
      case HEALTH_CONNECT, HEALTHKIT -> 3;
      case SENSOR, GPS -> 2;
      case MANUAL -> 1;
    };
  }

  private String tieKey(ActivityLog activity) {
    return String.join("|",
        Objects.toString(activity.getId(), ""),
        Objects.toString(activity.getCreatedAt(), ""),
        Objects.toString(activity.getClientUuid(), ""),
        Objects.toString(activity.getStartedAt(), ""),
        Objects.toString(activity.getActivityType().getCode(), ""));
  }

  private <T extends Comparable<? super T>> int compareDesc(T left, T right) {
    if (left == null && right == null) return 0;
    if (left == null) return 1;
    if (right == null) return -1;
    return right.compareTo(left);
  }
}
