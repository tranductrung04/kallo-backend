package com.healthcare.kallo.dto.response;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    UserSummary user,
    boolean profileCompleted) {}
