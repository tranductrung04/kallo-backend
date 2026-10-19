package com.healthcare.kallo.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.google")
public record GoogleProperties(String clientIds) {
  public List<String> clientIdList() {
    return clientIds == null || clientIds.isBlank()
        ? List.of()
        : Arrays.stream(clientIds.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
  }
}
