package com.healthcare.kallo.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class DailyGoalPolicyTest {
  private final DailyGoalPolicy policy = new DailyGoalPolicy(2, 10);

  @Test
  void requiresMinimumMealCount() {
    assertThat(policy.isMet(1, 2_000, 2_000)).isFalse();
    assertThat(policy.isMet(2, 2_000, 2_000)).isTrue();
  }

  @Test
  void acceptsBothCalorieToleranceBoundaries() {
    assertThat(policy.isMet(2, 1_800, 2_000)).isTrue();
    assertThat(policy.isMet(2, 2_200, 2_000)).isTrue();
  }

  @Test
  void rejectsInvalidOrOutsideCalorieValues() {
    assertThat(policy.isMet(2, 1_799, 2_000)).isFalse();
    assertThat(policy.isMet(2, 2_201, 2_000)).isFalse();
    assertThat(policy.isMet(2, null, 2_000)).isFalse();
    assertThat(policy.isMet(2, 2_000, null)).isFalse();
    assertThat(policy.isMet(2, 2_000, 0)).isFalse();
    assertThat(policy.isMet(-1, 2_000, 2_000)).isFalse();
  }

  @Test
  void acceptsDailyProgressOverload() {
    assertThat(policy.isMet(new DailyProgress(null, 2, 2_000, 2_000, 0, null, 0, null))).isTrue();
    assertThat(policy.isMet((DailyProgress) null)).isFalse();
  }

  @Test
  void handlesLargeIntegersWithoutOverflow() {
    assertThat(policy.isMet(2, Integer.MAX_VALUE, Integer.MAX_VALUE)).isTrue();
  }

  @Test
  void validatesConstructorArguments() {
    assertThatIllegalArgumentException().isThrownBy(() -> new DailyGoalPolicy(0, 10));
    assertThatIllegalArgumentException().isThrownBy(() -> new DailyGoalPolicy(2, -1));
    assertThatIllegalArgumentException().isThrownBy(() -> new DailyGoalPolicy(2, 101));
  }
}
