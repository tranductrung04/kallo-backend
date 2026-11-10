package com.healthcare.kallo.dto.request.auth;

public record LogoutRequest(String refreshToken, Boolean allDevices) {}
