package com.healthcare.kallo.service.auth;

import com.healthcare.kallo.model.auth.User;

import java.util.UUID;

public interface TokenService {
  TokenPair issue(User user);

  String issueAccessToken(User user);

  TokenPair rotate(String refreshToken);

  void revoke(String refreshToken);

  void revokeAll(User user);

  void revokeAllByToken(String refreshToken);

  long accessTokenLifetimeSeconds();

  record TokenPair(UUID userId, String accessToken, String refreshToken) {}
}
