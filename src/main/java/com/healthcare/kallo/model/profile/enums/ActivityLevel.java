package com.healthcare.kallo.model.profile.enums;

public enum ActivityLevel {
  SEDENTARY(1.2),
  LIGHTLY_ACTIVE(1.375),
  MODERATELY_ACTIVE(1.55),
  VERY_ACTIVE(1.725),
  EXTRA_ACTIVE(1.9);

  private final double multiplier;

  ActivityLevel(double multiplier) {
    this.multiplier = multiplier;
  }

  public double getMultiplier() {
    return multiplier;
  }
}
