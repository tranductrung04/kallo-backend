package com.healthcare.kallo.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;

@ConfigurationProperties("app.notification")
@Validated
public record NotificationProperties(
    @Min(1) int retentionDays,
    @Min(1) int reminderDailyCap,
    @Min(1) int maxDevicesPerUser,
    @Min(1) int deviceRetentionDays,
    @NotNull Duration reminderCatchUp) {
  @AssertTrue
  public boolean reminderCatchUpIsPositive() {
    return reminderCatchUp != null && !reminderCatchUp.isNegative() && !reminderCatchUp.isZero();
  }
}
