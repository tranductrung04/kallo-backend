package com.healthcare.kallo.service.game.impl;

import com.healthcare.kallo.model.game.Badge;
import com.healthcare.kallo.model.game.enums.BadgeRuleType;
import com.healthcare.kallo.service.game.BadgeRule;
import com.healthcare.kallo.service.game.BadgeRuleContext;
import org.springframework.stereotype.Component;

/**
 * Evaluates historical progress because badge evaluation may run after the log transaction.
 * Idempotent granting means a later evaluation cannot award this badge twice.
 */
@Component
public class FirstLogRule implements BadgeRule {
  @Override public BadgeRuleType type() { return BadgeRuleType.FIRST_LOG; }
  @Override public boolean isSatisfied(BadgeRuleContext context, Badge badge) {
    return context.firstLog() && BadgeRuleSupport.atLeast(1L, badge);
  }
}
