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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {
  private static final String DUMMY_PASSWORD_HASH =
      "$2a$12$LQv3c1yqBWVHxkd0L5k8jO6QJt8PjKq5J6O2m0q2gK8K5vS6G0m8W";
  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final TokenService tokens;
  private final UserMapper mapper;
  private final ProfileStatusProvider profileStatus;

  public AuthServiceImpl(
      UserRepository u, PasswordEncoder e, TokenService t, UserMapper m, ProfileStatusProvider p) {
    users = u;
    encoder = e;
    tokens = t;
    mapper = m;
    profileStatus = p;
  }

  public AuthResponse register(RegisterRequest r) {
    String email = EmailNormalizer.normalize(r.email());
    if (users.existsByNormalizedEmail(email))
      throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
    User u = new User();
    u.setEmail(email);
    u.setPasswordHash(encoder.encode(r.password()));
    u.setDisplayName(r.displayName());
    u.setAuthProvider(AuthProvider.LOCAL);
    u.setEmailVerified(false);
    u = users.save(u);
    return response(u);
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
    return response(u);
  }

  private AuthResponse response(User u) {
    return new AuthResponse(
        tokens.accessToken(u),
        tokens.refreshToken(u),
        "Bearer",
        tokens.accessTokenLifetimeSeconds(),
        mapper.toSummary(u),
        profileStatus.isCompleted(u));
  }
}
