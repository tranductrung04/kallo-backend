package com.healthcare.kallo.service.game.impl;

import com.healthcare.kallo.model.game.Badge;
import com.healthcare.kallo.model.game.enums.BadgeRuleType;
import com.healthcare.kallo.service.game.BadgeRule;
import com.healthcare.kallo.service.game.BadgeRuleContext;
import org.springframework.stereotype.Component;

@Component
public class ChallengesCompletedRule implements BadgeRule {
  @Override public BadgeRuleType type() { return BadgeRuleType.CHALLENGES_COMPLETED; }
  @Override public boolean isSatisfied(BadgeRuleContext context, Badge badge) {
    return BadgeRuleSupport.atLeast(context.challengesCompleted(), badge);
  }
}
