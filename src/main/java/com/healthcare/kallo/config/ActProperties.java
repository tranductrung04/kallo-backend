package com.healthcare.kallo.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.act")
public record ActProperties(
    @Min(1) int maxSyncBatchSize,
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
    @NotNull Duration tombstoneRetention) {

  @AssertTrue(message = "weightMinKg must be less than weightMaxKg")
  public boolean isWeightRangeValid() {
    return weightMinKg != null && weightMaxKg != null && weightMinKg.compareTo(weightMaxKg) < 0;
  }
}
