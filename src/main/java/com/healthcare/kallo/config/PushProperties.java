package com.healthcare.kallo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.AssertTrue;

@ConfigurationProperties("app.push")
@Validated
public record PushProperties(boolean enabled, String credentialsPath) {
  @AssertTrue(message = "app.push.credentials-path is required when app.push.enabled is true")
  public boolean hasCredentialsWhenEnabled() {
    return !enabled || (credentialsPath != null && !credentialsPath.isBlank());
  }
}
