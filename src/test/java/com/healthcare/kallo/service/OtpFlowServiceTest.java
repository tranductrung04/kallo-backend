package com.healthcare.kallo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.config.AuthProperties;
import com.healthcare.kallo.model.EmailVerificationToken;
import com.healthcare.kallo.model.PasswordResetToken;
import com.healthcare.kallo.model.User;
import com.healthcare.kallo.repository.EmailVerificationTokenRepository;
import com.healthcare.kallo.repository.PasswordResetTokenRepository;
import com.healthcare.kallo.repository.UserRepository;
import java.time.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class OtpFlowServiceTest {
  private static final AuthProperties PROPERTIES =
      new AuthProperties(5, Duration.ofMinutes(15), Duration.ofMinutes(15), 5,
          Duration.ofSeconds(60), 3);

  @Mock UserRepository users;
  @Mock EmailVerificationTokenRepository verificationTokens;
  @Mock PasswordResetTokenRepository resetTokens;
  @Mock EmailService email;
  @Mock PasswordEncoder encoder;
  @Mock TokenService tokenService;

  @Test
  void verificationCodeIsLockedAfterFiveInvalidAttempts() {
    UUID id = UUID.randomUUID();
    User user = user(id, false);
    EmailVerificationToken token = new EmailVerificationToken();
    token.setUser(user);
    token.setCodeHash(new OtpService("otp-test-secret-with-at-least-32-bytes").hash("EMAIL_VERIFY", id, "123456"));
    token.setCreatedAt(Instant.now());
    token.setExpiresAt(Instant.now().plus(Duration.ofMinutes(15)));
    when(users.findById(id)).thenReturn(Optional.of(user));
    when(verificationTokens.findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(id))
        .thenReturn(Optional.of(token));

    EmailVerificationService service =
        new EmailVerificationService(users, verificationTokens,
            new OtpService("otp-test-secret-with-at-least-32-bytes"), email, PROPERTIES);

    for (int i = 0; i < 5; i++) {
      assertThrows(BusinessException.class, () -> service.verify(id, "000000"));
    }

    assertThat(token.getAttempts()).isEqualTo(5);
    assertThat(token.getUsedAt()).isNotNull();
    verify(verificationTokens, times(5)).save(token);
    assertThrows(BusinessException.class, () -> service.verify(id, "123456"));
  }

  @Test
  void resetCodeCannotUseEmailVerificationHash() {
    UUID id = UUID.randomUUID();
    User user = user(id, false);
    PasswordResetToken token = new PasswordResetToken();
    token.setUser(user);
    OtpService otp = new OtpService("otp-test-secret-with-at-least-32-bytes");
    token.setCodeHash(otp.hash("EMAIL_VERIFY", id, "123456"));
    token.setCreatedAt(Instant.now());
    token.setExpiresAt(Instant.now().plus(Duration.ofMinutes(15)));
    when(users.findByNormalizedEmail("user@example.com")).thenReturn(Optional.of(user));
    when(resetTokens.findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(id))
        .thenReturn(Optional.of(token));

    PasswordResetService service =
        new PasswordResetService(users, resetTokens, otp, email, encoder, tokenService, PROPERTIES);

    assertThrows(BusinessException.class,
        () -> service.reset("user@example.com", "123456", "NewPassword1"));
    assertThat(token.getAttempts()).isEqualTo(1);
    verify(encoder, never()).encode(any());
  }

  @Test
  void expiredVerificationCodeIsRejectedAndInvalidated() {
    UUID id = UUID.randomUUID();
    User user = user(id, false);
    EmailVerificationToken token = new EmailVerificationToken();
    token.setUser(user);
    token.setCodeHash(new OtpService("otp-test-secret-with-at-least-32-bytes").hash("EMAIL_VERIFY", id, "123456"));
    token.setCreatedAt(Instant.now().minus(Duration.ofHours(1)));
    token.setExpiresAt(Instant.now().minusSeconds(1));
    when(users.findById(id)).thenReturn(Optional.of(user));
    when(verificationTokens.findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(id))
        .thenReturn(Optional.of(token));

    EmailVerificationService service =
        new EmailVerificationService(users, verificationTokens,
            new OtpService("otp-test-secret-with-at-least-32-bytes"), email, PROPERTIES);

    assertThrows(BusinessException.class, () -> service.verify(id, "123456"));
    assertThat(token.getUsedAt()).isNotNull();
    verify(verificationTokens).save(token);
  }

  @Test
  void verificationResendIsLimitedToThreeCodesPerHour() {
    UUID id = UUID.randomUUID();
    User user = user(id, false);
    when(users.findById(id)).thenReturn(Optional.of(user));
    when(verificationTokens.findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(id))
        .thenReturn(Optional.empty());
    when(verificationTokens.countByUserIdAndCreatedAtAfter(eq(id), any(Instant.class)))
        .thenReturn(3L);

    EmailVerificationService service =
        new EmailVerificationService(users, verificationTokens,
            new OtpService("otp-test-secret-with-at-least-32-bytes"), email, PROPERTIES);

    assertThrows(BusinessException.class, () -> service.resend(id));
    verify(verificationTokens, never()).save(any(EmailVerificationToken.class));
  }

  @Test
  void passwordResetResendIsLimitedToThreeCodesPerHour() {
    UUID id = UUID.randomUUID();
    User user = user(id, false);
    when(users.findByNormalizedEmail("user@example.com")).thenReturn(Optional.of(user));
    when(resetTokens.countByUserIdAndCreatedAtAfter(eq(id), any(Instant.class))).thenReturn(3L);

    PasswordResetService service =
        new PasswordResetService(users, resetTokens,
            new OtpService("otp-test-secret-with-at-least-32-bytes"), email, encoder,
            tokenService, PROPERTIES);

    service.requestReset("user@example.com");
    verify(resetTokens, never()).save(any(PasswordResetToken.class));
    verify(email, never()).sendPasswordResetCode(any(User.class), any());
  }

  @Test
  void passwordResetRejectsCodeAfterFixedClockExpiry() {
    UUID id = UUID.randomUUID();
    Instant now = Instant.parse("2026-10-21T00:00:00Z");
    User user = user(id, false);
    PasswordResetToken token = new PasswordResetToken();
    token.setUser(user);
    OtpService otp = new OtpService("otp-test-secret-with-at-least-32-bytes");
    token.setCodeHash(otp.hash("PASSWORD_RESET", id, "123456"));
    token.setCreatedAt(now.minus(Duration.ofMinutes(16)));
    token.setExpiresAt(now.minusSeconds(1));
    when(users.findByNormalizedEmail("user@example.com")).thenReturn(Optional.of(user));
    when(resetTokens.findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(id))
        .thenReturn(Optional.of(token));

    PasswordResetService service =
        new PasswordResetService(users, resetTokens, otp, email, encoder, tokenService,
            PROPERTIES, Clock.fixed(now, ZoneOffset.UTC));

    assertThrows(BusinessException.class,
        () -> service.reset("user@example.com", "123456", "NewPassword1"));
    assertThat(token.getUsedAt()).isEqualTo(now);
    verify(resetTokens).save(token);
  }

  private User user(UUID id, boolean verified) {
    User user = new User();
    user.setId(id);
    user.setEmail("user@example.com");
    user.setDisplayName("User");
    user.setEmailVerified(verified);
    return user;
  }
}
