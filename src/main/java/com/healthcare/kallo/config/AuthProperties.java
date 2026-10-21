package com.healthcare.kallo.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public record AuthProperties(
    int maxLoginAttempts,
    Duration lockDuration,
    Duration otpExpiration,
    int maxOtpAttempts,
    Duration resendInterval,
    int maxOtpPerHour) {
}
