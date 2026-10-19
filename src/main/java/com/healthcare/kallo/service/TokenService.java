package com.healthcare.kallo.service;

import com.healthcare.kallo.model.User;

public interface TokenService {
  String accessToken(User user);

  String refreshToken(User user);

  long accessTokenLifetimeSeconds();
}
