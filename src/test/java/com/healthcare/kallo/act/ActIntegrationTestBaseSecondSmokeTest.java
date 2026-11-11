package com.healthcare.kallo.act;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ActIntegrationTestBaseSecondSmokeTest extends ActIntegrationTestBase {
  @Test
  void secondTestClassUsesTheSamePostgresContainer() {
    assertThat(POSTGRES.isRunning()).isTrue();
    assertThat(POSTGRES.getJdbcUrl()).isEqualTo(ActIntegrationTestBase.POSTGRES.getJdbcUrl());
  }
}
