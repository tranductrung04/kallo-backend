package com.healthcare.kallo.dto.response.game;

import java.time.Instant;
import java.util.UUID;

public record FriendResponse(UUID id, String displayName, String avatarUrl, Instant since) {}
