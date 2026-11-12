package com.healthcare.kallo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;

@ConfigurationProperties("app.attribution")
@Validated
public record AttributionProperties(
    @NotBlank String openFoodFactsUrl,
    @NotBlank String openFoodFactsLicense,
    @NotBlank String openFoodFactsAttribution,
    @NotBlank String ninUrl,
    @NotBlank String ninLicense,
    @NotBlank String ninAttribution,
    @NotBlank String faoInfoodsUrl,
    @NotBlank String faoInfoodsLicense,
    @NotBlank String faoInfoodsAttribution) {}
