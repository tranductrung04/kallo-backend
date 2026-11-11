package com.healthcare.kallo.service.act;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CalorieCalculator {
  private CalorieCalculator() {}

  public static BigDecimal calculate(BigDecimal met, BigDecimal weightKg, int durationMin) {
    return met.multiply(weightKg)
        .multiply(BigDecimal.valueOf(durationMin))
        .divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP);
  }
}
