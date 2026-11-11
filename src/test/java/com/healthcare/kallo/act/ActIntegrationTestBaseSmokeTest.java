package com.healthcare.kallo.act;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ActIntegrationTestBaseSmokeTest extends ActIntegrationTestBase {
  @Test
  void sharedPostgresContainerIsRunning() {
    assertThat(POSTGRES.isRunning()).isTrue();
  }
}
