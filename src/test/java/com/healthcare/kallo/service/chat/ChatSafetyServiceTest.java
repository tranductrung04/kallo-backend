package com.healthcare.kallo.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.healthcare.kallo.common.BusinessException;
import org.junit.jupiter.api.Test;

class ChatSafetyServiceTest {
  private final ChatSafetyService safety = new ChatSafetyService();

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
  void replacesOutputContainingAllergen() {
    var result = safety.validateOutputWithGuardrails("Gợi ý món có tôm", java.util.List.of("đậu phộng"));
    assertThat(result.replaced()).isFalse();

    result = safety.validateOutputWithGuardrails("Gợi ý món có tôm", java.util.List.of("tôm"));
    assertThat(result.replaced()).isTrue();
    assertThat(result.content()).doesNotContain("tôm");
  }
}
