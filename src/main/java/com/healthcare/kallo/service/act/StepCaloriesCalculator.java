package com.healthcare.kallo.service.act;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class StepCaloriesCalculator {
  private StepCaloriesCalculator() {}

  public static BigDecimal calculate(long steps, BigDecimal weightKg, BigDecimal heightCm,
      BigDecimal walkingMet, BigDecimal walkingSpeedKmh, int fallbackCadenceSpm,
      BigDecimal strideFactor) {
    if (steps <= 0 || weightKg == null) return BigDecimal.ZERO.setScale(1);
    BigDecimal hours = heightCm == null
        ? BigDecimal.valueOf(steps)
            .divide(BigDecimal.valueOf(fallbackCadenceSpm * 60L), 10, RoundingMode.HALF_UP)
        : BigDecimal.valueOf(steps).multiply(strideFactor).multiply(heightCm.movePointLeft(2))
            .divide(BigDecimal.valueOf(1000L), 10, RoundingMode.HALF_UP)
            .divide(walkingSpeedKmh, 10, RoundingMode.HALF_UP);
    return walkingMet.multiply(weightKg).multiply(hours).setScale(1, RoundingMode.HALF_UP);
  }
}
