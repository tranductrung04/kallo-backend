package com.healthcare.kallo.service.act;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class WhrRiskCalculator {
  private WhrRiskCalculator() {}

  public record Result(BigDecimal whr, String riskLevel) {}

  public static Result calculate(BigDecimal waistCm, BigDecimal hipCm, String gender) {
    BigDecimal whr = waistCm.divide(hipCm, 4, RoundingMode.HALF_UP);
    // Frontend classifyWhrRisk thresholds must replace this fallback once supplied.
    BigDecimal moderate = "female".equalsIgnoreCase(gender) ? new BigDecimal("0.80") : new BigDecimal("0.90");
    BigDecimal high = "female".equalsIgnoreCase(gender) ? new BigDecimal("0.85") : new BigDecimal("0.95");
    String risk = whr.compareTo(high) >= 0 ? "high" : whr.compareTo(moderate) >= 0 ? "moderate" : "low";
    return new Result(whr, risk);
  }
}
