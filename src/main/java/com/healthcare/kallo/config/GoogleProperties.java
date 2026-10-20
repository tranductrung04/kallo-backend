package com.healthcare.kallo.config;

import java.util.Arrays;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("app.google")
@Validated
public record GoogleProperties(@NotBlank String clientIds, @NotBlank String jwksUri) {
  public List<String> clientIdList() {
    return clientIds == null || clientIds.isBlank()
        ? List.of()
        : Arrays.stream(clientIds.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
  }
}
