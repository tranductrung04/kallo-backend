package com.healthcare.kallo.dto.response.game;

import java.util.UUID;

public record LeaderboardEntryResponse(
    UUID userId,
    String displayName,
    String avatarUrl,
    long rank,
    long value) {}
