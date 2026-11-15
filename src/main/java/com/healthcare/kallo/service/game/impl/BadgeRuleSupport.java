package com.healthcare.kallo.service.game.impl;

import com.healthcare.kallo.model.game.Badge;

final class BadgeRuleSupport {
  private BadgeRuleSupport() {}

  static boolean atLeast(Integer value, Badge badge) {
    return badge.getThreshold() != null && value != null && value >= badge.getThreshold();
  }

  static boolean atLeast(long value, Badge badge) {
    return badge.getThreshold() != null && value >= badge.getThreshold();
  }
}
