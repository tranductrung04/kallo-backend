package com.healthcare.kallo.model.profile.enums;

import java.math.BigDecimal;

public enum ActivityLevel {
  SEDENTARY(new BigDecimal("1.2")),
  LIGHTLY_ACTIVE(new BigDecimal("1.375")),
  MODERATELY_ACTIVE(new BigDecimal("1.55")),
  VERY_ACTIVE(new BigDecimal("1.725")),
  EXTRA_ACTIVE(new BigDecimal("1.9"));

  private final BigDecimal pal;

  ActivityLevel(BigDecimal pal) {
    this.pal = pal;
  }

  public BigDecimal getPal() {
    return pal;
  }
}
