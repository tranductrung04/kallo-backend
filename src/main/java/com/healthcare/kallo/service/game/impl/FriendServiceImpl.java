package com.healthcare.kallo.service.game.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.GameProperties;
import com.healthcare.kallo.service.game.FriendsCountBadgeHook;
import com.healthcare.kallo.dto.request.game.RespondFriendRequest;
import com.healthcare.kallo.dto.request.game.SendFriendRequest;
import com.healthcare.kallo.dto.response.game.FriendPageResponse;
import com.healthcare.kallo.dto.response.game.FriendRequestResponse;
import com.healthcare.kallo.dto.response.game.FriendResponse;
import com.healthcare.kallo.dto.response.game.Relationship;
import com.healthcare.kallo.dto.response.game.UserSummaryResponse;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.auth.enums.UserStatus;
import com.healthcare.kallo.model.game.Friendship;
import com.healthcare.kallo.model.game.enums.FriendshipStatus;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.game.FriendshipRepository;
import com.healthcare.kallo.repository.game.UserGameSettingsRepository;
import com.healthcare.kallo.service.auth.StorageService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FriendServiceImpl implements com.healthcare.kallo.service.game.FriendService {
  private final FriendshipRepository friendships;
  private final UserGameSettingsRepository settings;
  private final UserRepository users;
  private final StorageService storage;
  private final GameProperties properties;
  private final FriendsCountBadgeHook badges;
  private final Clock clock;
  private final java.time.Duration avatarExpiration = java.time.Duration.ofMinutes(15);

  public FriendServiceImpl(FriendshipRepository friendships, UserGameSettingsRepository settings,
      UserRepository users, StorageService storage, GameProperties properties, FriendsCountBadgeHook badges, Clock clock) {
    this.friendships = friendships;
    this.settings = settings;
    this.users = users;
    this.storage = storage;
    this.properties = properties;
    this.badges = badges;
    this.clock = clock;
  }

  @Override
  @Transactional
  public FriendRequestResponse sendRequest(UUID userId, SendFriendRequest request) {
    User requester = user(userId);
    User target = request.userId() == null
        ? settingsUser(userId, request.friendCode()) : user(request.userId());
    if (userId.equals(target.getId())) throw new BusinessException(ErrorCode.CANNOT_FRIEND_SELF);
    if (target.getStatus() != UserStatus.ACTIVE || !target.isEmailVerified())
      throw new BusinessException(ErrorCode.NOT_FOUND);
    Friendship friendship = friendships.findBetweenForUpdate(userId, target.getId()).orElse(null);
    Instant now = Instant.now(clock);
    if (friendship != null) {
      if (friendship.getStatus() == FriendshipStatus.ACCEPTED)
        throw new BusinessException(ErrorCode.ALREADY_FRIENDS);
      if (friendship.getStatus() == FriendshipStatus.PENDING) {
        if (friendship.getRequester().getId().equals(userId))
          throw new BusinessException(ErrorCode.FRIEND_REQUEST_PENDING);
        enforceAcceptedLimits(userId, target.getId());
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendship.setRespondedAt(now);
        badges.onAccepted(userId);
        badges.onAccepted(target.getId());
        return response(friendship);
      }
      if (friendship.getRequester().getId().equals(userId)
          && friendship.getRespondedAt() != null
          && now.isBefore(friendship.getRespondedAt().plus(properties.friend().declineCooldown())))
        throw new BusinessException(ErrorCode.FRIEND_REQUEST_COOLDOWN);
      friendships.delete(friendship);
      friendships.flush();
    }
    enforceRequestLimits(userId, target.getId());
    Friendship created = new Friendship();
    created.setRequester(requester);
    created.setAddressee(target);
    created.setStatus(FriendshipStatus.PENDING);
    return response(friendships.saveAndFlush(created));
  }

  private User settingsUser(UUID userId, String friendCode) {
    return settings.findActiveVerifiedByFriendCode(userId, friendCode.toUpperCase(java.util.Locale.ROOT))
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
  }

  private void enforceRequestLimits(UUID requester, UUID target) {
    if (friendships.countAcceptedByUserId(requester) >= properties.friend().maxFriends()
        || friendships.countAcceptedByUserId(target) >= properties.friend().maxFriends()
        || friendships.countByRequesterIdAndStatus(requester, FriendshipStatus.PENDING) >= properties.friend().maxPendingOut())
      throw new BusinessException(ErrorCode.FRIEND_LIMIT_REACHED);
  }

  private void enforceAcceptedLimits(UUID first, UUID second) {
    if (friendships.countAcceptedByUserId(first) >= properties.friend().maxFriends()
        || friendships.countAcceptedByUserId(second) >= properties.friend().maxFriends())
      throw new BusinessException(ErrorCode.FRIEND_LIMIT_REACHED);
  }

  @Override
  @Transactional
  public FriendRequestResponse respond(UUID userId, UUID requestId, RespondFriendRequest request) {
    Friendship friendship = friendships.findById(requestId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    if (!friendship.getAddressee().getId().equals(userId) || friendship.getStatus() != FriendshipStatus.PENDING)
      throw new BusinessException(ErrorCode.NOT_FOUND);
    if ("ACCEPT".equals(request.action())) {
      enforceAcceptedLimits(friendship.getRequester().getId(), friendship.getAddressee().getId());
      friendship.setStatus(FriendshipStatus.ACCEPTED);
      badges.onAccepted(friendship.getRequester().getId());
      badges.onAccepted(friendship.getAddressee().getId());
    } else {
      friendship.setStatus(FriendshipStatus.DECLINED);
    }
    friendship.setRespondedAt(Instant.now(clock));
    return response(friendship);
  }

  @Override
  @Transactional
  public void cancel(UUID userId, UUID requestId) {
    Friendship friendship = friendships.findById(requestId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    if (!friendship.getRequester().getId().equals(userId) || friendship.getStatus() != FriendshipStatus.PENDING)
      throw new BusinessException(ErrorCode.NOT_FOUND);
    friendships.delete(friendship);
  }

  @Override
  @Transactional(readOnly = true)
  public List<FriendRequestResponse> listRequests(UUID userId, boolean incoming) {
    List<Friendship> rows = incoming
        ? friendships.findByAddresseeIdAndStatusOrderByCreatedAtDesc(userId, FriendshipStatus.PENDING)
        : friendships.findByRequesterIdAndStatusOrderByCreatedAtDesc(userId, FriendshipStatus.PENDING);
    return rows.stream().map(this::response).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public FriendPageResponse listFriends(UUID userId, int page, int size) {
    if (page < 0 || size < 1 || size > 200) throw new BusinessException(ErrorCode.INVALID_PARAMETER);
    var slice = friendships.findByUserAndStatus(userId, FriendshipStatus.ACCEPTED,
        PageRequest.of(page, size));
    return new FriendPageResponse(slice.getContent().stream().map(f -> {
      User friend = f.getRequester().getId().equals(userId) ? f.getAddressee() : f.getRequester();
      return new FriendResponse(friend.getId(), friend.getDisplayName(), avatar(friend.getAvatarUrl()), f.getRespondedAt());
    }).toList(), page, size, slice.hasNext());
  }

  @Override
  @Transactional
  public void unfriend(UUID userId, UUID friendUserId) {
    friendships.deleteAcceptedPair(userId, friendUserId, friendUserId, userId);
  }

  private void enforceLimits(UUID requester, UUID target) {
    if (friendships.countAcceptedByUserId(requester) >= properties.friend().maxFriends()
        || friendships.countAcceptedByUserId(target) >= properties.friend().maxFriends()
        || friendships.countByRequesterIdAndStatus(requester, FriendshipStatus.PENDING) >= properties.friend().maxPendingOut())
      throw new BusinessException(ErrorCode.FRIEND_LIMIT_REACHED);
  }

  private User user(UUID id) {
    return users.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
  }

  private FriendRequestResponse response(Friendship friendship) {
    return FriendRequestResponse.from(friendship, summary(friendship.getRequester()), summary(friendship.getAddressee()));
  }

  private UserSummaryResponse summary(User user) {
    return UserSummaryResponse.from(user, Relationship.NONE, avatar(user.getAvatarUrl()));
  }

  private String avatar(String key) {
    return key == null ? null : storage.presignedUrl(key, avatarExpiration);
  }
}
