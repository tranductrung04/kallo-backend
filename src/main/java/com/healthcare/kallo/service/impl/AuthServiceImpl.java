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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthServiceImpl implements AuthService {
  private static final String DUMMY_PASSWORD_HASH =
      "$2a$12$LQv3c1yqBWVHxkd0L5k8jO6QJt8PjKq5J6O2m0q2gK8K5vS6G0m8W";
  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final TokenService tokens;
  private final UserMapper mapper;
  private final ProfileStatusProvider profileStatus;
  private final TransactionTemplate txTemplate;

  public AuthServiceImpl(
      UserRepository u,
      PasswordEncoder e,
      TokenService t,
      UserMapper m,
      ProfileStatusProvider p,
      TransactionTemplate txTemplate) {
    users = u;
    encoder = e;
    tokens = t;
    mapper = m;
    profileStatus = p;
    this.txTemplate = txTemplate;
  }

  public AuthResponse register(RegisterRequest r) {
    String email = EmailNormalizer.normalize(r.email());
    if (users.existsByNormalizedEmail(email))
      throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
    String encodedPassword = encoder.encode(r.password());
    return txTemplate.execute(
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

  private AuthResponse response(User u) {
    return response(u, tokens.issue(u));
  }

  private AuthResponse response(User u, TokenService.TokenPair pair) {
    return new AuthResponse(
        pair.accessToken(),
        pair.refreshToken(),
        "Bearer",
        tokens.accessTokenLifetimeSeconds(),
        mapper.toSummary(u),
        profileStatus.isCompleted(u));
  }

}
