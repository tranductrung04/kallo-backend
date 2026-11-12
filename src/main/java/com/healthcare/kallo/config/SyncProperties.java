package com.healthcare.kallo.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

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
  }

  @AssertTrue(message = "app.sync.default-page must not exceed app.sync.max-page")
  public boolean pageBoundsAreValid() {
    return defaultPage <= maxPage;
  }

  @AssertTrue
  public boolean durationsArePositive() {
    return isPositive(settleDelay) && isPositive(tombstoneRetention) && isPositive(maxFutureSkew);
  }

  private static boolean isPositive(Duration value) {
    return value != null && !value.isNegative() && !value.isZero();
  }
}
