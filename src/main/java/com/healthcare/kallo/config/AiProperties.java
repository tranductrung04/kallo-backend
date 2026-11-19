package com.healthcare.kallo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(String serviceUrl, int timeoutMs) {
  public AiProperties {
    if (serviceUrl == null || serviceUrl.isBlank()) {
      serviceUrl = "http://localhost:8000";
    }
    if (timeoutMs <= 0) {
      timeoutMs = 3000;
    }
  }
}
