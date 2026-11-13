package com.healthcare.kallo.config;

import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("app.websocket")
@Validated
public record WebSocketProperties(
    List<String> allowedOrigins,
    @Min(1) int maxSessionsPerUser) {
  public WebSocketProperties {
    allowedOrigins = allowedOrigins == null || allowedOrigins.isEmpty() ? List.of("*") : List.copyOf(allowedOrigins);
  }
}
