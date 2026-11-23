package com.healthcare.kallo.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.healthcare.kallo.common.BusinessException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class ChatSafetyServiceTest {
  private final ChatSafetyService safety = new ChatSafetyService(new ChatMetrics(new SimpleMeterRegistry()));

  @Test
  void trimsAndRemovesControlCharacters() {
    assertThat(safety.validateAndNormalizeInput("  Xin\u0000 chào  ", 20)).isEqualTo("Xin chào");
  }

  @Test
  void rejectsOversizedInput() {
    assertThatThrownBy(() -> safety.validateAndNormalizeInput("12345", 4))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Payload");
  }

  @Test
  void ignoresNegatedAllergen() {
    var result = safety.validateOutputWithGuardrails("Mon khong co tom", java.util.List.of("tom"));
    assertThat(result.replaced()).isFalse();
  }

  @Test
  void doesNotMatchAccentedShortWordsAfterNormalization() {
    assertThat(safety.validateOutputWithGuardrails("Cà phê sữa và calo trung bình", java.util.List.of("ca"))
        .replaced()).isFalse();
    assertThat(safety.validateOutputWithGuardrails("Cà phê sữa và calo trung bình", java.util.List.of("trung"))
        .replaced()).isFalse();
    assertThat(safety.validateOutputWithGuardrails("Mẹ bạn uống cà phê", java.util.List.of("me"))
        .replaced()).isFalse();
    assertThat(safety.validateOutputWithGuardrails("Lắc đều trước khi dùng", java.util.List.of("lac"))
        .replaced()).isFalse();
  }

  @Test
  void matchesAccentedAllergenTerms() {
    assertThat(safety.validateOutputWithGuardrails("Cá nướng", java.util.List.of("ca")).replaced()).isTrue();
    assertThat(safety.validateOutputWithGuardrails("Mè rang", java.util.List.of("me")).replaced()).isTrue();
    assertThat(safety.validateOutputWithGuardrails("Trứng luộc", java.util.List.of("trung")).replaced()).isTrue();
    assertThat(safety.validateOutputWithGuardrails("Lạc rang", java.util.List.of("lac")).replaced()).isTrue();
  }

  @Test
  void replacesOutputContainingAllergen() {
    var result = safety.validateOutputWithGuardrails("Gợi ý món có tôm", java.util.List.of("đậu phộng"));
    assertThat(result.replaced()).isFalse();

    result = safety.validateOutputWithGuardrails("Gợi ý món có tôm", java.util.List.of("tôm"));
    assertThat(result.replaced()).isTrue();
    assertThat(result.content()).doesNotContain("tôm");
  }
}
