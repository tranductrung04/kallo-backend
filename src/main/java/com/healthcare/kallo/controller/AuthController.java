package com.healthcare.kallo.controller;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.request.LoginRequest;
import com.healthcare.kallo.dto.request.ChangePasswordRequest;
import com.healthcare.kallo.dto.request.LogoutRequest;
import com.healthcare.kallo.dto.request.RefreshTokenRequest;
import com.healthcare.kallo.dto.request.RegisterRequest;
import com.healthcare.kallo.dto.request.GoogleLoginRequest;
import com.healthcare.kallo.dto.request.*;
import com.healthcare.kallo.dto.response.AuthResponse;
import com.healthcare.kallo.security.CurrentUser;
import com.healthcare.kallo.service.AuthService;
import com.healthcare.kallo.service.EmailVerificationService;
import com.healthcare.kallo.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService service;
  private final EmailVerificationService emailVerification;
  private final PasswordResetService passwordReset;

  public AuthController(AuthService service, EmailVerificationService emailVerification,
      PasswordResetService passwordReset) {
    this.service = service;
    this.emailVerification = emailVerification;
    this.passwordReset = passwordReset;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest r) {
    return ApiResponse.success(service.register(r));
  }

  @PostMapping("/login")
  public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest r) {
    return ApiResponse.success(service.login(r));
  }

  @PostMapping("/google")
  public ApiResponse<AuthResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest r) {
    return ApiResponse.success(service.loginWithGoogle(r.idToken()));
  }

  @PostMapping("/refresh")
  public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest r) {
    return ApiResponse.success(service.refresh(r.refreshToken()));
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@RequestBody(required = false) LogoutRequest r) {
    if (r == null) {
      return;
    }
    service.logout(r.refreshToken(), Boolean.TRUE.equals(r.allDevices()));
  }

  @PostMapping("/change-password")
  public ApiResponse<AuthResponse> changePassword(
      @Valid @RequestBody ChangePasswordRequest r, Authentication authentication) {
    return ApiResponse.success(
        service.changePassword(
            CurrentUser.id(authentication), r.currentPassword(), r.newPassword()));
  }

  @PostMapping("/forgot-password")
  public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest r) {
    passwordReset.requestReset(r.email());
    return ApiResponse.success(null);
  }

  @PostMapping("/reset-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resetPassword(@Valid @RequestBody ResetPasswordRequest r) {
    passwordReset.reset(r.email(), r.code(), r.newPassword());
  }

  @PostMapping("/verify-email")
  public ApiResponse<AuthResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest r,
      Authentication authentication) {
    return ApiResponse.success(service.verifyEmail(CurrentUser.id(authentication), r.code()));
  }

  @PostMapping("/resend-verification")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resendVerification(Authentication authentication) {
    emailVerification.resend(CurrentUser.id(authentication));
  }
}
