package com.healthcare.kallo.service.auth;

import com.healthcare.kallo.dto.request.auth.LoginRequest;
import com.healthcare.kallo.dto.request.auth.RegisterRequest;
import com.healthcare.kallo.dto.response.auth.AuthResponse;
import java.util.UUID;

public interface AuthService {
  AuthResponse register(RegisterRequest request);

  AuthResponse login(LoginRequest request);

  AuthResponse loginWithGoogle(String idToken);

  AuthResponse refresh(String refreshToken);

  void logout(String refreshToken, boolean allDevices);

  AuthResponse changePassword(UUID userId, String currentPassword, String newPassword);

  AuthResponse verifyEmail(UUID userId, String code);
}
