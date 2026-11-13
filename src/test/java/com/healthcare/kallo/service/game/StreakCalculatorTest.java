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
}
