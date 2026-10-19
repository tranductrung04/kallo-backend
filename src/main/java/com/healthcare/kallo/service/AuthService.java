package com.healthcare.kallo.service;

import com.healthcare.kallo.dto.request.LoginRequest;
import com.healthcare.kallo.dto.request.RegisterRequest;
import com.healthcare.kallo.dto.response.AuthResponse;
import java.util.UUID;

public interface AuthService {
  AuthResponse register(RegisterRequest request);

  AuthResponse login(LoginRequest request);

  AuthResponse refresh(String refreshToken);

  void logout(String refreshToken, boolean allDevices);

  AuthResponse changePassword(UUID userId, String currentPassword, String newPassword);
}
