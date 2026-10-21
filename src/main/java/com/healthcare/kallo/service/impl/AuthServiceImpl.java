package com.healthcare.kallo.service.impl;

import com.healthcare.kallo.common.*;
import com.healthcare.kallo.dto.request.LoginRequest;
import com.healthcare.kallo.dto.request.RegisterRequest;
import com.healthcare.kallo.dto.response.AuthResponse;
import com.healthcare.kallo.mapper.UserMapper;
import com.healthcare.kallo.model.*;
import com.healthcare.kallo.model.enums.AuthProvider;
import com.healthcare.kallo.model.enums.UserStatus;
import com.healthcare.kallo.repository.UserRepository;
import com.healthcare.kallo.service.*;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AuthServiceImpl implements AuthService {
  private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
  private static final String DUMMY_PASSWORD_HASH =
      "$2a$12$LQv3c1yqBWVHxkd0L5k8jO6QJt8PjKq5J6O2m0q2gK8K5vS6G0m8W";
  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final TokenService tokens;
  private final UserMapper mapper;
  private final ProfileStatusProvider profileStatus;
  private final TransactionTemplate txTemplate;
  private final GoogleTokenVerifier googleTokenVerifier;
  private final EmailVerificationService emailVerification;

  public AuthServiceImpl(
      UserRepository u,
      PasswordEncoder e,
      TokenService t,
      UserMapper m,
      ProfileStatusProvider p,
      TransactionTemplate txTemplate,
      GoogleTokenVerifier googleTokenVerifier, EmailVerificationService emailVerification) {
    users = u;
    encoder = e;
    tokens = t;
    mapper = m;
    profileStatus = p;
    this.txTemplate = txTemplate;
    this.googleTokenVerifier = googleTokenVerifier;
    this.emailVerification = emailVerification;
  }

  public AuthResponse register(RegisterRequest r) {
    String email = EmailNormalizer.normalize(r.email());
    if (users.existsByNormalizedEmail(email))
      throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
    String encodedPassword = encoder.encode(r.password());
    AuthResponse response = txTemplate.execute(
        status -> {
          if (users.existsByNormalizedEmail(email))
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
          User u = new User();
          u.setEmail(email);
          u.setPasswordHash(encodedPassword);
          u.setDisplayName(r.displayName());
          u.setAuthProvider(AuthProvider.LOCAL);
          u.setEmailVerified(false);
          return response(users.save(u));
        });
    try {
      emailVerification.sendCode(response.user().id());
    } catch (RuntimeException e) {
      log.warn("Unable to create verification code for user {}", response.user().id(), e);
    }
    return response;
  }

  public AuthResponse login(LoginRequest r) {
    String email = EmailNormalizer.normalize(r.email());
    User u = users.findByNormalizedEmail(email).orElse(null);
    String hash =
        u == null || u.getPasswordHash() == null ? DUMMY_PASSWORD_HASH : u.getPasswordHash();
    boolean valid = encoder.matches(r.password(), hash);
    if (!valid) throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    if (u == null) throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    if (u.getStatus() == UserStatus.DISABLED)
      throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
    User authenticatedUser = u;
    return txTemplate.execute(status -> response(authenticatedUser));
  }

  @Override
  public AuthResponse loginWithGoogle(String idToken) {
    GoogleTokenVerifier.GoogleClaims claims = googleTokenVerifier.verify(idToken);
    String email = EmailNormalizer.normalize(claims.email());
    try {
      return googleLoginTx(claims, email);
    } catch (DataIntegrityViolationException e) {
      return googleLoginTx(claims, email);
    }
  }

  private AuthResponse googleLoginTx(
      GoogleTokenVerifier.GoogleClaims claims, String email) {
    return txTemplate.execute(
        status -> {
          User user = users.findByGoogleSub(claims.subject()).orElse(null);
          boolean isNewUser = false;
          if (user == null) {
            user = users.findByNormalizedEmail(email).orElse(null);
            if (user == null) {
              user = new User();
              user.setEmail(email);
              user.setDisplayName(displayName(claims));
              user.setAuthProvider(AuthProvider.GOOGLE);
              isNewUser = true;
            } else {
              if (user.getGoogleSub() != null) {
                throw new BusinessException(ErrorCode.GOOGLE_ACCOUNT_CONFLICT);
              }
              if (!user.isEmailVerified()) {
                user.setPasswordHash(null);
                tokens.revokeAll(user);
              }
            }
            user.setGoogleSub(claims.subject());
            user.setEmailVerified(true);
          } else {
            if (user.getStatus() == UserStatus.DISABLED) {
              throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
            }
          }
          if (user.getStatus() == UserStatus.DISABLED) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
          }
          user = users.save(user);
          return response(user, tokens.issue(user), isNewUser);
        });
  }

  public AuthResponse refresh(String refreshToken) {
    TokenService.TokenPair pair = tokens.rotate(refreshToken);
    User user =
        users.findById(pair.userId()).orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));
    return response(user, pair);
  }

  public void logout(String refreshToken, boolean allDevices) {
    if (refreshToken == null || refreshToken.isBlank()) {
      return;
    }
    if (allDevices) {
      tokens.revokeAllByToken(refreshToken);
    } else {
      tokens.revoke(refreshToken);
    }
  }

  public AuthResponse changePassword(UUID userId, String currentPassword, String newPassword) {
    User u = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    if (u.getPasswordHash() == null) throw new BusinessException(ErrorCode.PASSWORD_NOT_SET);
    if (!encoder.matches(currentPassword, u.getPasswordHash()))
      throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
    if (encoder.matches(newPassword, u.getPasswordHash()))
      throw new BusinessException(ErrorCode.PASSWORD_UNCHANGED);
    String encoded = encoder.encode(newPassword);
    return txTemplate.execute(
        status -> {
          User managed =
              users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
          managed.setPasswordHash(encoded);
          users.save(managed);
          tokens.revokeAll(managed);
          return response(managed);
        });
  }

  @Override
  public AuthResponse verifyEmail(UUID userId, String code) {
    emailVerification.verify(userId, code);
    User user = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    return new AuthResponse(
        tokens.issueAccessToken(user),
        null,
        "Bearer",
        tokens.accessTokenLifetimeSeconds(),
        mapper.toSummary(user),
        profileStatus.isCompleted(user),
        false);
  }

  private AuthResponse response(User u) {
    return response(u, tokens.issue(u), false);
  }

  private AuthResponse response(User u, TokenService.TokenPair pair) {
    return response(u, pair, false);
  }

  private AuthResponse response(User u, TokenService.TokenPair pair, boolean isNewUser) {
    return new AuthResponse(
        pair.accessToken(),
        pair.refreshToken(),
        "Bearer",
        tokens.accessTokenLifetimeSeconds(),
        mapper.toSummary(u),
        profileStatus.isCompleted(u),
        isNewUser);
  }

  private String displayName(GoogleTokenVerifier.GoogleClaims claims) {
    String name =
        claims.name() != null && !claims.name().isBlank()
            ? claims.name().trim()
            : fallbackDisplayName(claims.email());
    return name.length() <= 100 ? name : name.substring(0, 100);
  }

  private String fallbackDisplayName(String email) {
    int at = email.indexOf('@');
    return at > 0 ? email.substring(0, at) : email;
  }

}
