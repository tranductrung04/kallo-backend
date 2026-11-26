package com.healthcare.kallo.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class NutriPropertiesTest {

  @Test
  void providesSafeInCodeDefaultsWhenNoYamlBlockIsDefined() {
    NutriProperties props = new NutriProperties();

    assertThat(props.getSugarWarningThresholdG()).isEqualByComparingTo(new BigDecimal("50.0"));
    assertThat(props.getProteinWarningRatio()).isEqualByComparingTo(new BigDecimal("1.2"));
    assertThat(props.getMaxImageBytes()).isEqualTo(2L * 1024 * 1024);
    assertThat(props.getAi().getBaseUrl()).isEqualTo("http://localhost:8000");
    assertThat(props.getAi().getConfidenceThreshold()).isEqualByComparingTo("0.4");
    assertThat(props.getAi().isMockEnabled()).isFalse();
    assertThat(props.getRateLimit().getRecognizePerMinute()).isEqualTo(10);
    assertThat(props.getStorage().getTempPrefix()).isEqualTo("temp/");
    assertThat(props.getStorage().getMealsPrefix()).isEqualTo("meals/");
    assertThat(props.getStorage().getTempRetention()).isEqualTo(Duration.ofHours(24));
  }
}
