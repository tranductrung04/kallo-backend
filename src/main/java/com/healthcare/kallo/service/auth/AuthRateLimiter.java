package com.healthcare.kallo.service.auth;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthRateLimiter {
  private static final Logger log = LoggerFactory.getLogger(AuthRateLimiter.class);
  private final RateLimiter limiter;
  private final int registerPerHour;
  private final int forgotPerHour;
  private final int resendPerHour;

  public AuthRateLimiter(
      RateLimiter limiter,
      @Value("${app.auth.rate-limit.register-per-hour:10}") int registerPerHour,
      @Value("${app.auth.rate-limit.forgot-per-hour:10}") int forgotPerHour,
      @Value("${app.auth.rate-limit.resend-per-hour:10}") int resendPerHour) {
    this.limiter = limiter;
    this.registerPerHour = positive(registerPerHour);
    this.forgotPerHour = positive(forgotPerHour);
    this.resendPerHour = positive(resendPerHour);
  }

  public void checkRegister(HttpServletRequest request) {
    check("register", request, registerPerHour);
  }

  public void checkForgotPassword(HttpServletRequest request) {
    check("forgot-password", request, forgotPerHour);
  }

  public void checkResendVerification(HttpServletRequest request) {
    check("resend-verification", request, resendPerHour);
  }

  private void check(String operation, HttpServletRequest request, int limit) {
    String ip = request.getRemoteAddr();
    if (ip == null || ip.isBlank()) ip = "unknown";
    try {
      RateLimiter.Result result = limiter.check("auth:" + operation + ":ip:" + hash(ip), limit, Duration.ofHours(1));
      if (!result.allowed()) {
        throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, result.retryAfterSeconds());
      }
    } catch (BusinessException e) {
      throw e;
    } catch (RuntimeException e) {
      log.warn("Auth rate limiter unavailable; allowing {} request", operation, e);
    }
  }

  private String hash(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static int positive(int value) {
    if (value < 1) throw new IllegalArgumentException("Auth rate limits must be positive");
    return value;
  }
}
