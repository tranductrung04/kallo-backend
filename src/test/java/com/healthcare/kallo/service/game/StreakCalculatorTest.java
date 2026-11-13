package com.healthcare.kallo.service.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StreakCalculatorTest {
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);
  private final StreakCalculator calculator = new StreakCalculator();

  @Test
  void noDatesReturnsEmptyResult() {
    assertThat(calculator.calculate(Set.of(), TODAY, 0))
        .isEqualTo(new StreakCalculator.Result(0, 0, null, false, false));
  }

  @Test
  void todayCompletedCountsCurrentStreak() {
    var result = calculator.calculate(Set.of(TODAY.minusDays(2), TODAY.minusDays(1), TODAY), TODAY, 0);

    assertThat(result.currentStreak()).isEqualTo(3);
    assertThat(result.longestStreak()).isEqualTo(3);
    assertThat(result.lastCompletedDate()).isEqualTo(TODAY);
    assertThat(result.todayCompleted()).isTrue();
    assertThat(result.atRisk()).isFalse();
  }

  @Test
  void unfinishedTodayKeepsYesterdayRunAtRisk() {
    var result = calculator.calculate(Set.of(TODAY.minusDays(2), TODAY.minusDays(1)), TODAY, 0);

    assertThat(result.currentStreak()).isEqualTo(2);
    assertThat(result.todayCompleted()).isFalse();
    assertThat(result.atRisk()).isTrue();
  }

  @Test
  void missedYesterdayResetsCurrentButKeepsLongest() {
    var result = calculator.calculate(Set.of(TODAY.minusDays(4), TODAY.minusDays(3)), TODAY, 4);

    assertThat(result.currentStreak()).isZero();
    assertThat(result.longestStreak()).isEqualTo(4);
    assertThat(result.atRisk()).isFalse();
  }

  @Test
  void backfillJoinsTwoRunsAndDuplicateDatesDoNotChangeResult() {
    var before = calculator.calculate(Set.of(TODAY.minusDays(3), TODAY.minusDays(1), TODAY), TODAY, 0);
    var after = calculator.calculate(Set.of(TODAY.minusDays(3), TODAY.minusDays(2), TODAY.minusDays(1), TODAY), TODAY, 0);

    assertThat(before.currentStreak()).isEqualTo(2);
    assertThat(after.currentStreak()).isEqualTo(4);
    assertThat(after.longestStreak()).isEqualTo(4);
  }

  @Test
  void storedLongestIsNeverLowered() {
    var result = calculator.calculate(Set.of(TODAY), TODAY, 10);

    assertThat(result.currentStreak()).isEqualTo(1);
    assertThat(result.longestStreak()).isEqualTo(10);
  }

  @Test
  void onlyTodayStartsOneDayStreak() {
    var result = calculator.calculate(Set.of(TODAY), TODAY, 0);

    assertThat(result).isEqualTo(new StreakCalculator.Result(1, 1, TODAY, true, false));
  }

  @Test
  void yesterdayAndTodayAreOneCurrentRun() {
    var result = calculator.calculate(Set.of(TODAY.minusDays(1), TODAY), TODAY, 0);

    assertThat(result.currentStreak()).isEqualTo(2);
    assertThat(result.longestStreak()).isEqualTo(2);
  }

  @Test
  void yesterdayOnlyIsAtRisk() {
    var result = calculator.calculate(Set.of(TODAY.minusDays(1)), TODAY, 0);

    assertThat(result).isEqualTo(
        new StreakCalculator.Result(1, 1, TODAY.minusDays(1), false, true));
  }

  @Test
  void gapBeforeYesterdayResetsCurrentStreak() {
    var result = calculator.calculate(Set.of(TODAY.minusDays(3), TODAY.minusDays(1)), TODAY, 0);

    assertThat(result.currentStreak()).isEqualTo(1);
    assertThat(result.atRisk()).isTrue();
  }

  @Test
  void gapBetweenTodayAndOlderRunDoesNotJoinCurrent() {
    var result = calculator.calculate(Set.of(TODAY.minusDays(3), TODAY), TODAY, 0);

    assertThat(result.currentStreak()).isEqualTo(1);
    assertThat(result.longestStreak()).isEqualTo(1);
  }

  @Test
  void removingMiddleDateSplitsFormerRun() {
    var result = calculator.calculate(
        Set.of(TODAY.minusDays(3), TODAY.minusDays(1), TODAY), TODAY, 0);

    assertThat(result.currentStreak()).isEqualTo(2);
    assertThat(result.longestStreak()).isEqualTo(2);
  }

  @Test
  void longestUsesOlderRunWhenCurrentRunIsEmpty() {
    var result = calculator.calculate(Set.of(TODAY.minusDays(6), TODAY.minusDays(5), TODAY.minusDays(4)), TODAY, 0);

    assertThat(result.currentStreak()).isZero();
    assertThat(result.longestStreak()).isEqualTo(3);
  }

  @Test
  void longestUsesTheLongestOfSeparateRuns() {
    var result = calculator.calculate(Set.of(
        TODAY.minusDays(6), TODAY.minusDays(5),
        TODAY.minusDays(3), TODAY.minusDays(2), TODAY.minusDays(1)), TODAY, 0);

    assertThat(result.longestStreak()).isEqualTo(3);
  }

  @Test
  void nullDatesAreTreatedAsEmpty() {
    var result = calculator.calculate(null, TODAY, 0);

    assertThat(result).isEqualTo(new StreakCalculator.Result(0, 0, null, false, false));
  }

  @Test
  void negativeStoredLongestIsClampedToZero() {
    assertThat(calculator.calculate(Set.of(), TODAY, -4).longestStreak()).isZero();
  }

  @Test
  void latestCompletedDateIsIndependentOfCurrentRun() {
    LocalDate latest = TODAY.plusDays(2);
    var result = calculator.calculate(Set.of(TODAY.minusDays(1), latest), TODAY, 0);

    assertThat(result.lastCompletedDate()).isEqualTo(latest);
  }

  @Test
  void duplicateSetValuesHaveNoEffect() {
    var single = calculator.calculate(Set.of(TODAY.minusDays(1), TODAY), TODAY, 0);
    var duplicate = calculator.calculate(
        new java.util.HashSet<>(java.util.List.of(TODAY.minusDays(1), TODAY, TODAY)), TODAY, 0);

    assertThat(duplicate).isEqualTo(single);
  }

  @Test
  void nullTodayIsRejected() {
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> calculator.calculate(Set.of(), null, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
