package com.healthcare.kallo.controller;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.request.LoginRequest;
import com.healthcare.kallo.dto.request.ChangePasswordRequest;
import com.healthcare.kallo.dto.request.LogoutRequest;
import com.healthcare.kallo.dto.request.RefreshTokenRequest;
import com.healthcare.kallo.dto.request.RegisterRequest;
import com.healthcare.kallo.dto.response.AuthResponse;
import com.healthcare.kallo.security.CurrentUser;
import com.healthcare.kallo.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService service;

  public AuthController(AuthService service) {
    this.service = service;
  }

  @PostMapping("/register")
  public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest r) {
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.register(r)));
  }

  @PostMapping("/login")
  public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest r) {
    return ApiResponse.success(service.login(r));
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
}
