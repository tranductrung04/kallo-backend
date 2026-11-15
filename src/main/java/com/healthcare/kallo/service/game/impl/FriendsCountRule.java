package com.healthcare.kallo.service.game.impl;

import com.healthcare.kallo.model.game.Badge;
import com.healthcare.kallo.model.game.enums.BadgeRuleType;
import com.healthcare.kallo.service.game.BadgeRule;
import com.healthcare.kallo.service.game.BadgeRuleContext;
import org.springframework.stereotype.Component;

@Component
public class FriendsCountRule implements BadgeRule {
  @Override public BadgeRuleType type() { return BadgeRuleType.FRIENDS_COUNT; }
  @Override public boolean isSatisfied(BadgeRuleContext context, Badge badge) {
    return BadgeRuleSupport.atLeast(context.friendsCount(), badge);
  }
}
