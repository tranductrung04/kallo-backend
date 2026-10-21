package com.healthcare.kallo.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("app.storage")
@Validated
public record StorageProperties(
    String endpoint,
    String accessKey,
    String secretKey,
    String bucket,
    String publicEndpoint,
    Duration presignedUrlExpiration) {}
