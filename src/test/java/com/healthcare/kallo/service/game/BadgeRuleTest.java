package com.healthcare.kallo.service.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.kallo.model.game.Badge;
import com.healthcare.kallo.model.game.enums.BadgeRuleType;
import com.healthcare.kallo.service.game.impl.ChallengesCompletedRule;
import com.healthcare.kallo.service.game.impl.FirstLogRule;
import com.healthcare.kallo.service.game.impl.FriendsCountRule;
import com.healthcare.kallo.service.game.impl.StreakDaysRule;
import com.healthcare.kallo.service.game.impl.WeightGoalReachedRule;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BadgeRuleTest {
  private static final UUID USER_ID = UUID.randomUUID();

  @Test
  void streakRuleUsesLongestStreakThreshold() {
    Badge badge = badge(BadgeRuleType.STREAK_DAYS, 7);
    var rule = new StreakDaysRule();

    assertThat(rule.isSatisfied(context(7, false, 0, 0, false), badge)).isTrue();
    assertThat(rule.isSatisfied(context(6, false, 0, 0, false), badge)).isFalse();
  }

  @Test
  void firstLogRuleRequiresLogAndPositiveThreshold() {
    Badge badge = badge(BadgeRuleType.FIRST_LOG, 1);
    var rule = new FirstLogRule();

    assertThat(rule.isSatisfied(context(0, true, 0, 0, false), badge)).isTrue();
    assertThat(rule.isSatisfied(context(0, false, 0, 0, false), badge)).isFalse();
    assertThat(rule.isSatisfied(context(0, true, 0, 0, false), badge(BadgeRuleType.FIRST_LOG, 2))).isFalse();
    assertThat(rule.isSatisfied(context(0, true, 0, 0, false), badge(BadgeRuleType.FIRST_LOG, null))).isFalse();
  }

  @Test
  void friendsRuleUsesAcceptedFriendCount() {
    Badge badge = badge(BadgeRuleType.FRIENDS_COUNT, 2);
    var rule = new FriendsCountRule();

    assertThat(rule.isSatisfied(context(0, false, 2, 0, false), badge)).isTrue();
    assertThat(rule.isSatisfied(context(0, false, 1, 0, false), badge)).isFalse();
  }

  @Test
  void challengesRuleUsesCompletedChallengeCount() {
    Badge badge = badge(BadgeRuleType.CHALLENGES_COMPLETED, 5);
    var rule = new ChallengesCompletedRule();

    assertThat(rule.isSatisfied(context(0, false, 0, 5, false), badge)).isTrue();
    assertThat(rule.isSatisfied(context(0, false, 0, 4, false), badge)).isFalse();
  }

  @Test
  void weightGoalRuleUsesContextFlag() {
    Badge badge = badge(BadgeRuleType.WEIGHT_GOAL_REACHED, null);
    var rule = new WeightGoalReachedRule();

    assertThat(rule.isSatisfied(context(0, false, 0, 0, true), badge)).isTrue();
    assertThat(rule.isSatisfied(context(0, false, 0, 0, false), badge)).isFalse();
  }

  private BadgeRuleContext context(int streak, boolean firstLog, long friends, long challenges,
      boolean weightGoalReached) {
    return new BadgeRuleContext(USER_ID, streak, firstLog, friends, challenges, weightGoalReached);
  }

  private Badge badge(BadgeRuleType type, Integer threshold) {
    Badge badge = new Badge();
    badge.setRuleType(type);
    badge.setThreshold(threshold);
    return badge;
  }
}
