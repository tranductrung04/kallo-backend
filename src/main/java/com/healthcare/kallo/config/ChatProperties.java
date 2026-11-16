package com.healthcare.kallo.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.chat")
public record ChatProperties(
    boolean enabled,
    @Valid @NotNull Ai ai,
    @Min(1) @Max(12) int historyWindow,
    @Min(1) @Max(2000) int maxMessageLength,
    @Valid @NotNull RateLimit rateLimit,
    @NotNull Duration pendingTimeout,
    @Min(1) int retentionDays) {

  @AssertTrue(message = "app.chat.pending-timeout must be at least AI read-timeout")
  public boolean hasValidPendingTimeout() {
    return ai == null || ai.readTimeout() == null || pendingTimeout == null
        || pendingTimeout.compareTo(ai.readTimeout()) >= 0;
  }

  public record Ai(
      @NotBlank String baseUrl,
      String apiKey,
      @NotNull Duration connectTimeout,
      @NotNull Duration readTimeout,
      boolean mockEnabled) {}

  public record RateLimit(
      @Min(1) int perMinute,
      @Min(1) int perDay) {}
}
