package com.healthcare.kallo.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.nutri")
@Getter
@Setter
public class NutriProperties {

  @Valid @NotNull private Ai ai = new Ai();
  @Valid @NotNull private RateLimit rateLimit = new RateLimit();
  @Valid @NotNull private Storage storage = new Storage();

  @NotNull
  @DecimalMin("0.0")
  private BigDecimal sugarWarningThresholdG = new BigDecimal("50.0");

  @NotNull
  @DecimalMin(value = "0.0", inclusive = false)
  private BigDecimal proteinWarningRatio = new BigDecimal("1.2");

  @Min(1024)
  private long maxImageBytes = 2L * 1024 * 1024;

  @Getter
  @Setter
  public static class Ai {
    @NotBlank private String baseUrl = "http://localhost:8000";
    private String apiKey = "";
    @NotNull private Duration connectTimeout = Duration.ofSeconds(10);
    @NotNull private Duration readTimeout = Duration.ofSeconds(35);

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private BigDecimal confidenceThreshold = new BigDecimal("0.4");

    private boolean mockEnabled = false;
  }

  @Getter
  @Setter
  public static class RateLimit {
    @Min(1) private int recognizePerMinute = 10;
  }

  @Getter
  @Setter
  public static class Storage {
    @NotBlank private String tempPrefix = "temp/";
    @NotBlank private String mealsPrefix = "meals/";
    @NotNull private Duration tempRetention = Duration.ofHours(24);
  }
}
