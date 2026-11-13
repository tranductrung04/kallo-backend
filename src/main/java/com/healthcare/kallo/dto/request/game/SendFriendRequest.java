package com.healthcare.kallo.dto.request.game;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record SendFriendRequest(
    @Pattern(regexp = "(?i)^[2-9A-HJKMNP-Z]{8}$", message = "must be an 8-character friend code")
        String friendCode,
    UUID userId) {
  @AssertTrue(message = "exactly one of friendCode or userId is required")
  public boolean hasExactlyOneTarget() {
    return (friendCode != null) != (userId != null);
  }
}
