package com.healthcare.kallo.service.act;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class StepCaloriesCalculatorTest {
  @Test
  void calculatesTheSpecificationExampleInCalories() {
    BigDecimal calories = StepCaloriesCalculator.calculate(
        10_000, new BigDecimal("70"), new BigDecimal("170"),
        new BigDecimal("3.5"), new BigDecimal("5"), 100, new BigDecimal("0.415"));

    assertThat(calories).isEqualByComparingTo("345.7");
  }
}
