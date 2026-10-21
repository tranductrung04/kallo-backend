package com.healthcare.kallo.service;

import com.healthcare.kallo.common.*;
import com.healthcare.kallo.config.AuthProperties;
import com.healthcare.kallo.model.*;
import com.healthcare.kallo.repository.*;
import java.time.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class PasswordResetService {
  private static final String PURPOSE = "PASSWORD_RESET";
  private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
  private final UserRepository users; private final PasswordResetTokenRepository tokens;
  private final OtpService otp; private final EmailService email; private final PasswordEncoder encoder;
  private final TokenService tokenService; private final AuthProperties props;
  private final Clock clock;

  @Autowired
  public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens, OtpService otp,
      EmailService email, PasswordEncoder encoder, TokenService tokenService, AuthProperties props) {
    this(users, tokens, otp, email, encoder, tokenService, props, Clock.systemUTC());
  }

  PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens, OtpService otp,
      EmailService email, PasswordEncoder encoder, TokenService tokenService, AuthProperties props,
      Clock clock) {
    this.users = users; this.tokens = tokens; this.otp = otp; this.email = email;
    this.encoder = encoder; this.tokenService = tokenService; this.props = props; this.clock = clock;
  }

  @Transactional
  public void requestReset(String emailAddress) {
    users.findByNormalizedEmail(EmailNormalizer.normalize(emailAddress)).ifPresent(user -> {
      Instant now = Instant.now(clock);
      if (tokens.countByUserIdAndCreatedAtAfter(user.getId(), now.minus(Duration.ofHours(1))) >= props.maxOtpPerHour()) {
        return;
      }
      var latest = tokens.findTopByUserIdOrderByCreatedAtDesc(user.getId());
      if (latest.isPresent() && latest.get().getCreatedAt().plus(props.resendInterval()).isAfter(now)) {
        return;
      }
      tokens.findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId()).ifPresent(t -> t.setUsedAt(now));
      String code = otp.generateCode();
      PasswordResetToken token = new PasswordResetToken();
      token.setUser(user); token.setCodeHash(otp.hash(PURPOSE, user.getId(), code));
      token.setExpiresAt(now.plus(props.otpExpiration())); token.setCreatedAt(now);
      tokens.save(token);
      dispatchAfterCommit(() -> email.sendPasswordResetCode(user, code));
    });
  }

  @Transactional(noRollbackFor = BusinessException.class)
  public void reset(String emailAddress, String code, String newPassword) {
    User user = users.findByNormalizedEmail(EmailNormalizer.normalize(emailAddress))
        .orElseThrow(() -> new BusinessException(ErrorCode.RESET_CODE_INVALID));
    PasswordResetToken token = tokens.findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId())
        .orElseThrow(() -> new BusinessException(ErrorCode.RESET_CODE_INVALID));
    Instant now = Instant.now(clock);
    boolean valid = token.getAttempts() < props.maxOtpAttempts() && token.getExpiresAt().isAfter(now)
        && otp.matches(token.getCodeHash(), otp.hash(PURPOSE, user.getId(), code));
    if (!valid) {
      token.setAttempts(token.getAttempts() + 1);
      if (token.getAttempts() >= props.maxOtpAttempts() || !token.getExpiresAt().isAfter(now)) token.setUsedAt(now);
      tokens.save(token); throw new BusinessException(ErrorCode.RESET_CODE_INVALID);
    }
    user.setPasswordHash(encoder.encode(newPassword)); user.setEmailVerified(true); token.setUsedAt(now);
    users.save(user); tokens.save(token); tokenService.revokeAll(user);
  }

  private void dispatchAfterCommit(Runnable action) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      action.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        try {
          action.run();
        } catch (RuntimeException e) {
          log.warn("Unable to dispatch authentication email after commit", e);
        }
      }
    });
  }
}
