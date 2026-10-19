package com.healthcare.kallo.controller;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.request.LoginRequest;
import com.healthcare.kallo.dto.request.RegisterRequest;
import com.healthcare.kallo.dto.response.AuthResponse;
import com.healthcare.kallo.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.*;
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
}
