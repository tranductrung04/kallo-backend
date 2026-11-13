package com.healthcare.kallo.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import com.healthcare.kallo.model.act.enums.HealthProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.scheduling.support.CronExpression;

@Validated
@ConfigurationProperties("app.act")
public record ActProperties(
    @Min(1) @jakarta.validation.constraints.Max(4095) int maxSyncBatchSize,
    @Min(1) int syncBackfillDays,
    @Min(1) int manualBackfillDays,
    @NotNull Duration futureTolerance,
    @NotNull @DecimalMin("0.0") BigDecimal weightMinKg,
    @NotNull @DecimalMin("0.0") BigDecimal weightMaxKg,
    @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal walkingMet,
    @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal walkingSpeedKmh,
    @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal strideFactor,
    @Min(1) int fallbackCadenceSpm,
    @NotNull List<String> stepSourcePriority,
    @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal dedupOverlapRatio,
    @NotNull Duration tombstoneRetention,
    @NotNull String tombstoneCleanupCron,
    @Min(1) @Max(102400) int routeMaxBytes,
    @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal gpsMaxSpeedKmh) {

  @AssertTrue(message = "weightMinKg must be less than weightMaxKg")
  public boolean isWeightRangeValid() {
    return weightMinKg != null && weightMaxKg != null && weightMinKg.compareTo(weightMaxKg) < 0;
  }

  @AssertTrue(message = "stepSourcePriority must contain only valid health providers")
  public boolean isStepSourcePriorityValid() {
    if (stepSourcePriority == null) return false;
    if (stepSourcePriority.stream().anyMatch(value -> value == null || value.isBlank())
        || stepSourcePriority.size() != stepSourcePriority.stream().distinct().count()) return false;
    try {
      return stepSourcePriority.stream().allMatch(value -> {
        HealthProvider.valueOf(value);
        return true;
      });
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  @AssertTrue(message = "tombstoneCleanupCron must be a valid cron expression")
  public boolean isTombstoneCleanupCronValid() {
    try {
      CronExpression.parse(tombstoneCleanupCron);
      return true;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }
}
