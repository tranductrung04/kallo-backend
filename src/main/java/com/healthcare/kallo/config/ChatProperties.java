package com.healthcare.kallo.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.chat")
public record ChatProperties(
    boolean enabled,
    @Valid @NotNull Llm llm,
    @Min(1) @Max(20) int historyWindow,
    @Min(500) int maxContextChars,
    @Min(1) @Max(2000) int maxMessageLength,
    @Valid @NotNull RateLimit rateLimit,
    @NotNull Duration pendingTimeout,
    @Min(1) int retentionDays,
    @NotNull Duration sseTimeout,
    @Min(1) int sseHeartbeatSeconds) {

  @AssertTrue(message = "app.chat.pending-timeout must be at least llm.read-timeout")
  public boolean hasValidPendingTimeout() {
    return llm == null || llm.readTimeout() == null || pendingTimeout == null
        || pendingTimeout.compareTo(llm.readTimeout()) >= 0;
  }

  public record Llm(
      @NotBlank String baseUrl,
      String apiKey,
      @NotBlank String model,
      @NotNull Duration connectTimeout,
      @NotNull Duration readTimeout,
      @DecimalMin("0.0") @DecimalMax("2.0") double temperature,
      @Min(64) int maxTokens) {}

  public record RateLimit(
      @Min(1) int perMinute,
      @Min(1) int perDay) {}
}
