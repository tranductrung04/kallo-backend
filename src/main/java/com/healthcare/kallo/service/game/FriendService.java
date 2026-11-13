package com.healthcare.kallo.service.game;

import com.healthcare.kallo.dto.request.game.RespondFriendRequest;
import com.healthcare.kallo.dto.request.game.SendFriendRequest;
import com.healthcare.kallo.dto.response.game.FriendPageResponse;
import com.healthcare.kallo.dto.response.game.FriendRequestResponse;
import java.util.List;
import java.util.UUID;

public interface FriendService {
  FriendRequestResponse sendRequest(UUID userId, SendFriendRequest request);
  FriendRequestResponse respond(UUID userId, UUID requestId, RespondFriendRequest request);
  void cancel(UUID userId, UUID requestId);
  List<FriendRequestResponse> listRequests(UUID userId, boolean incoming);
  FriendPageResponse listFriends(UUID userId, int page, int size);
  void unfriend(UUID userId, UUID friendUserId);
}
