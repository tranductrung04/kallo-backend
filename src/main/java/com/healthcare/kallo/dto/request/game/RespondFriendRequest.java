package com.healthcare.kallo.dto.request.game;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RespondFriendRequest(
    @NotBlank @Pattern(regexp = "^(ACCEPT|DECLINE)$") String action) {}
