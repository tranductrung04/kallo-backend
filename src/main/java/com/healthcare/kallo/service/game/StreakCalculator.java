package com.healthcare.kallo.service.game;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

public final class StreakCalculator {
  public Result calculate(Set<LocalDate> metDates, LocalDate today, int storedLongest) {
    if (today == null) throw new IllegalArgumentException("today must not be null");
    Set<LocalDate> dates = metDates == null ? Set.of() : new HashSet<>(metDates);
    LocalDate latest = dates.stream().max(LocalDate::compareTo).orElse(null);
    boolean todayCompleted = dates.contains(today);
    LocalDate currentEnd = todayCompleted ? today : today.minusDays(1);
    int current = dates.contains(currentEnd) ? consecutive(dates, currentEnd) : 0;
    int longest = Math.max(Math.max(0, storedLongest), longest(dates));
    boolean atRisk = !todayCompleted && current > 0;
    return new Result(current, longest, latest, todayCompleted, atRisk);
  }

  private int consecutive(Set<LocalDate> dates, LocalDate end) {
    int count = 0;
    for (LocalDate date = end; dates.contains(date); date = date.minusDays(1)) count++;
    return count;
  }

  private int longest(Set<LocalDate> dates) {
    int longest = 0;
    for (LocalDate date : dates) {
      if (!dates.contains(date.minusDays(1))) {
        int count = 0;
        for (LocalDate current = date; dates.contains(current); current = current.plusDays(1)) count++;
        longest = Math.max(longest, count);
      }
    }
    return longest;
  }

  public record Result(
      int currentStreak,
      int longestStreak,
      LocalDate lastCompletedDate,
      boolean todayCompleted,
      boolean atRisk) {}
}
