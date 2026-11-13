package com.healthcare.kallo.service.game.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.GameProperties;
import com.healthcare.kallo.dto.response.game.Relationship;
import com.healthcare.kallo.dto.response.game.UserSummaryResponse;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.game.Friendship;
import com.healthcare.kallo.model.game.enums.FriendshipStatus;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.game.UserGameSettingsRepository;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.healthcare.kallo.repository.game.FriendshipRepository;
import com.healthcare.kallo.service.auth.StorageService;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSearchServiceImpl implements com.healthcare.kallo.service.game.UserSearchService {
  private final UserRepository users;
  private final UserGameSettingsRepository settings;
  private final FriendshipRepository friendships;
  private final StorageService storage;
  private final GameProperties properties;
  private final java.time.Duration avatarExpiration = java.time.Duration.ofMinutes(15);

  public UserSearchServiceImpl(UserRepository users, UserGameSettingsRepository settings,
      FriendshipRepository friendships, StorageService storage, GameProperties properties) {
    this.users = users;
    this.settings = settings;
    this.friendships = friendships;
    this.storage = storage;
    this.properties = properties;
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserSummaryResponse> search(UUID userId, String query) {
    if (query == null || query.isBlank()) throw new BusinessException(ErrorCode.INVALID_PARAMETER);
    String value = query.trim();
    List<User> matches;
    String code = value.toUpperCase(Locale.ROOT);
    if (code.matches("^[2-9A-HJKMNP-Z]{8}$")) {
      matches = settings.findActiveVerifiedByFriendCode(userId, code).stream().toList();
    } else {
      if (value.length() < properties.search().minLength()) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
      matches = settings.searchDiscoverable(userId, escape(value.toLowerCase(Locale.ROOT)) + "%",
          PageRequest.of(0, properties.search().maxResults()));
    }
    Map<UUID, Friendship> relations = matches.isEmpty() ? Map.of() : friendships.findBetweenAny(userId,
        matches.stream().map(User::getId).collect(Collectors.toSet())).stream()
        .collect(Collectors.toMap(f -> otherUserId(userId, f), Function.identity()));
    return matches.stream().map(user -> {
      Friendship relation = relations.get(user.getId());
      return UserSummaryResponse.from(user, relationship(userId, relation), avatar(user.getAvatarUrl()));
    }).toList();
  }

  private UUID otherUserId(UUID userId, Friendship friendship) {
    return friendship.getRequester().getId().equals(userId)
        ? friendship.getAddressee().getId() : friendship.getRequester().getId();
  }

  private Relationship relationship(UUID userId, Friendship friendship) {
    if (friendship == null) return Relationship.NONE;
    if (friendship.getStatus() == FriendshipStatus.ACCEPTED) return Relationship.FRIENDS;
    if (friendship.getStatus() != FriendshipStatus.PENDING) return Relationship.NONE;
    return friendship.getRequester().getId().equals(userId) ? Relationship.PENDING_OUT : Relationship.PENDING_IN;
  }

  private String escape(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private String avatar(String key) {
    return key == null ? null : storage.presignedUrl(key, avatarExpiration);
  }
}
