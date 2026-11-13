package com.healthcare.kallo.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("app.sync")
@Validated
public record SyncProperties(
    @Min(1) @Max(10000) int maxOperations,
    @NotNull Duration settleDelay,
    @NotNull Duration tombstoneRetention,
    @Min(1) @Max(10000) int defaultPage,
    @Min(1) @Max(10000) int maxPage,
    @NotNull Duration maxFutureSkew) {
  public SyncProperties {
    if (defaultPage > maxPage) {
      throw new IllegalArgumentException("app.sync.default-page must not exceed app.sync.max-page");
    }
    requirePositive(settleDelay, "app.sync.settle-delay");
    requirePositive(tombstoneRetention, "app.sync.tombstone-retention");
    requirePositive(maxFutureSkew, "app.sync.max-future-skew");
  }

  private static void requirePositive(Duration value, String name) {
    if (value == null || value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException(name + " must be positive");
    }
  }
}
