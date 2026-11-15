package com.healthcare.kallo.service.game;

import com.healthcare.kallo.model.game.Badge;
import com.healthcare.kallo.model.game.enums.BadgeRuleType;

public interface BadgeRule {
  BadgeRuleType type();

  boolean isSatisfied(BadgeRuleContext context, Badge badge);
}
