package com.healthcare.kallo.service;

import com.healthcare.kallo.model.User;

import java.util.UUID;

public interface TokenService {
  TokenPair issue(User user);

  TokenPair rotate(String refreshToken);

  void revoke(String refreshToken);

  void revokeAll(User user);

  void revokeAllByToken(String refreshToken);

  long accessTokenLifetimeSeconds();

  record TokenPair(UUID userId, String accessToken, String refreshToken) {}
}
