package com.healthcare.kallo.dto.response.game;

import com.healthcare.kallo.model.auth.User;
import java.util.UUID;

public record UserSummaryResponse(UUID id, String displayName, String avatarUrl, Relationship relationship) {
  public static UserSummaryResponse from(User user, Relationship relationship, String avatarUrl) {
    return new UserSummaryResponse(user.getId(), user.getDisplayName(), avatarUrl, relationship);
  }
}
