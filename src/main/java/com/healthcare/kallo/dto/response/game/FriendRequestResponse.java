package com.healthcare.kallo.dto.response.game;

import com.healthcare.kallo.model.game.Friendship;
import com.healthcare.kallo.model.game.enums.FriendshipStatus;
import java.time.Instant;
import java.util.UUID;

public record FriendRequestResponse(
    UUID id, UserSummaryResponse requester, UserSummaryResponse addressee,
    FriendshipStatus status, Instant createdAt, Instant respondedAt) {
  public static FriendRequestResponse from(Friendship friendship, UserSummaryResponse requester,
      UserSummaryResponse addressee) {
    return new FriendRequestResponse(friendship.getId(), requester, addressee, friendship.getStatus(),
        friendship.getCreatedAt(), friendship.getRespondedAt());
  }
}
