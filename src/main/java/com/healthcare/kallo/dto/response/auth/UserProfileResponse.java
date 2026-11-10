package com.healthcare.kallo.dto.response.auth;

import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String email,
    boolean emailVerified,
    String displayName,
    String timezone,
    String avatarUrl,
    String role) {}
