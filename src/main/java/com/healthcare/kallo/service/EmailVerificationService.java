package com.healthcare.kallo.service;

import com.healthcare.kallo.common.*;
import com.healthcare.kallo.config.AuthProperties;
import com.healthcare.kallo.model.*;
import com.healthcare.kallo.repository.*;
import java.time.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EmailVerificationService {
  private static final String PURPOSE = "EMAIL_VERIFY";
  private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
  private final UserRepository users;
  private final EmailVerificationTokenRepository tokens;
  private final OtpService otp;
  private final EmailService email;
  private final AuthProperties props;
  private final Clock clock;

  @Autowired
  public EmailVerificationService(UserRepository users, EmailVerificationTokenRepository tokens,
      OtpService otp, EmailService email, AuthProperties props) {
    this(users, tokens, otp, email, props, Clock.systemUTC());
  }
  EmailVerificationService(UserRepository users, EmailVerificationTokenRepository tokens,
      OtpService otp, EmailService email, AuthProperties props, Clock clock) {
    this.users = users; this.tokens = tokens; this.otp = otp; this.email = email; this.props = props; this.clock = clock;
  }

  @Transactional
  public void sendCode(UUID userId) {
    User user = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    if (user.isEmailVerified()) return;
    Instant now = Instant.now(clock);
    tokens.invalidateActive(userId, now);
    String code = otp.generateCode();
    EmailVerificationToken token = new EmailVerificationToken();
    token.setUser(user); token.setCodeHash(otp.hash(PURPOSE, userId, code));
    token.setExpiresAt(now.plus(props.otpExpiration())); token.setCreatedAt(now);
    tokens.save(token);
    dispatchAfterCommit(() -> email.sendVerificationCode(user, code));
  }

  @Transactional(noRollbackFor = BusinessException.class)
  public void verify(UUID userId, String code) {
    User user = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID));
    if (user.isEmailVerified()) return;
    EmailVerificationToken token = tokens.findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID));
    Instant now = Instant.now(clock);
    boolean valid = token.getAttempts() < props.maxOtpAttempts()
        && token.getExpiresAt().isAfter(now)
        && otp.matches(token.getCodeHash(), otp.hash(PURPOSE, userId, code));
    if (!valid) {
      token.setAttempts(token.getAttempts() + 1);
      if (token.getAttempts() >= props.maxOtpAttempts() || !token.getExpiresAt().isAfter(now)) token.setUsedAt(now);
      tokens.save(token); throw new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID);
    }
    token.setUsedAt(now); user.setEmailVerified(true); users.save(user); tokens.save(token);
  }

  @Transactional
  public void resend(UUID userId) {
    User user = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    if (user.isEmailVerified()) throw new BusinessException(ErrorCode.EMAIL_ALREADY_VERIFIED);
    Instant now = Instant.now(clock);
    var latest = tokens.findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(userId);
    if (latest.isPresent() && latest.get().getCreatedAt().plus(props.resendInterval()).isAfter(now)
        || tokens.countByUserIdAndCreatedAtAfter(userId, now.minus(Duration.ofHours(1))) >= props.maxOtpPerHour()) {
      throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
    }
    sendCode(userId);
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
