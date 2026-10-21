package com.healthcare.kallo.service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OtpService {
  private static final SecureRandom RANDOM = new SecureRandom();
  private final byte[] secret;

  public OtpService(@Value("${app.otp-hmac-secret}") String secret) {
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalStateException("OTP_HMAC_SECRET must contain at least 32 bytes");
    }
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
  }

  public String generateCode() {
    return "%06d".formatted(RANDOM.nextInt(1_000_000));
  }

  public String hash(String purpose, UUID userId, String code) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return HexFormat.of().formatHex(
          mac.doFinal(("%s:%s:%s".formatted(purpose, userId, code)).getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Unable to hash OTP", e);
    }
  }

  public boolean matches(String expected, String actual) {
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.US_ASCII), actual.getBytes(StandardCharsets.US_ASCII));
  }
}
