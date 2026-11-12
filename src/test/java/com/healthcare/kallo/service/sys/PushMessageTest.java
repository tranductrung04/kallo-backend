package com.healthcare.kallo.service.sys;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PushMessageTest {
  @Test
  void requiredDataCannotBeNull() {
    assertThatThrownBy(() -> new PushMessage(null, "id", "title", "body"))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("type must not be null");
  }
}
