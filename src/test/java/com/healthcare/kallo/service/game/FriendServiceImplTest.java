package com.healthcare.kallo.service.game;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.GamePropertiesFixture;
import com.healthcare.kallo.dto.request.game.RespondFriendRequest;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.game.Friendship;
import com.healthcare.kallo.model.game.enums.FriendshipStatus;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.game.FriendshipRepository;
import com.healthcare.kallo.repository.game.UserGameSettingsRepository;
import com.healthcare.kallo.service.auth.StorageService;
import com.healthcare.kallo.service.game.GameAuditLogger;
import com.healthcare.kallo.service.game.impl.FriendServiceImpl;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class FriendServiceImplTest {
  @Test
  void respondForeignRequestReturnsNotFound() {
    UUID owner = UUID.randomUUID();
    UUID foreign = UUID.randomUUID();
    UUID requestId = UUID.randomUUID();
    Friendship friendship = new Friendship();
    friendship.setId(requestId);
    User addressee = new User();
    addressee.setId(foreign);
    friendship.setAddressee(addressee);
    friendship.setStatus(FriendshipStatus.PENDING);
    FriendshipRepository friendships = mock(FriendshipRepository.class);
    when(friendships.findById(requestId)).thenReturn(Optional.of(friendship));
    FriendServiceImpl service = service(friendships);

    assertThatThrownBy(() -> service.respond(owner, requestId, new RespondFriendRequest("ACCEPT")))
        .satisfies(exception -> {
          org.assertj.core.api.Assertions.assertThat(exception)
              .isInstanceOf(com.healthcare.kallo.common.BusinessException.class);
          org.assertj.core.api.Assertions.assertThat(((com.healthcare.kallo.common.BusinessException) exception)
              .getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        });
  }

  @Test
  void cancelForeignRequestReturnsNotFound() {
    UUID owner = UUID.randomUUID();
    UUID foreign = UUID.randomUUID();
    UUID requestId = UUID.randomUUID();
    Friendship friendship = new Friendship();
    User requester = new User();
    requester.setId(foreign);
    friendship.setRequester(requester);
    friendship.setStatus(FriendshipStatus.PENDING);
    FriendshipRepository friendships = mock(FriendshipRepository.class);
    when(friendships.findById(requestId)).thenReturn(Optional.of(friendship));
    FriendServiceImpl service = service(friendships);

    assertThatThrownBy(() -> service.cancel(owner, requestId))
        .isInstanceOf(com.healthcare.kallo.common.BusinessException.class)
        .extracting(exception -> ((com.healthcare.kallo.common.BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.NOT_FOUND);
  }

  private FriendServiceImpl service(FriendshipRepository friendships) {
    return new FriendServiceImpl(friendships, mock(UserGameSettingsRepository.class), mock(UserRepository.class),
        mock(StorageService.class), GamePropertiesFixture.defaults(), mock(ApplicationEventPublisher.class),
        Clock.fixed(Instant.parse("2026-11-16T00:00:00Z"), ZoneOffset.UTC), mock(GameAuditLogger.class));
  }
}

