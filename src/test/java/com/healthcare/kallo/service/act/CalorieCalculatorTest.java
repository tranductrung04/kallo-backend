package com.healthcare.kallo.service.act;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CalorieCalculatorTest {
  @Test
  void calculatesCaloriesUsingMetWeightAndDuration() {
    assertThat(CalorieCalculator.calculate(new BigDecimal("8.3"), new BigDecimal("70"), 30))
        .isEqualByComparingTo("290.5");
  }

  @Test
  void roundsHalfUpAtHalfCenticalorie() {
    assertThat(CalorieCalculator.calculate(new BigDecimal("1.05"), new BigDecimal("20"), 1))
        .isEqualByComparingTo("0.4");
    assertThat(CalorieCalculator.calculate(new BigDecimal("25.0"), new BigDecimal("300"), 1440))
        .isEqualByComparingTo("180000.0");
  }
}
