package com.healthcare.kallo.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.healthcare.kallo.event.game.BadgeEarnedEvent;
import com.healthcare.kallo.event.game.ChallengeCompletedEvent;
import com.healthcare.kallo.event.game.FriendRequestAcceptedEvent;
import com.healthcare.kallo.event.game.FriendRequestReceivedEvent;
import com.healthcare.kallo.service.sys.RealtimePublisher;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class GameNotificationEventListenerTest {
  @Test
  void publishesFriendEventsToGameQueue() {
    GameNotifier notifier = Mockito.mock(GameNotifier.class);
    RealtimePublisher realtime = Mockito.mock(RealtimePublisher.class);
    GameNotificationEventListener listener = new GameNotificationEventListener(notifier, realtime);
    UUID recipient = UUID.randomUUID();
    UUID request = UUID.randomUUID();
    UUID friend = UUID.randomUUID();

    listener.onFriendRequest(new FriendRequestReceivedEvent(recipient, request, friend));
    listener.onFriendAccepted(new FriendRequestAcceptedEvent(recipient, friend));

    verify(realtime).sendToUser(recipient, "/queue/game-events", "FRIEND_REQUEST_RECEIVED",
        java.util.Map.of("requestId", request, "user", java.util.Map.of("id", friend)));
    verify(realtime).sendToUser(recipient, "/queue/game-events", "FRIEND_REQUEST_ACCEPTED",
        java.util.Map.of("user", java.util.Map.of("id", friend)));
  }

  @Test
  void notificationFailureDoesNotEscapeListener() {
    GameNotifier notifier = Mockito.mock(GameNotifier.class);
    RealtimePublisher realtime = Mockito.mock(RealtimePublisher.class);
    doThrow(new IllegalStateException()).when(notifier).badgeEarned(Mockito.any());

    new GameNotificationEventListener(notifier, realtime)
        .onBadge(new BadgeEarnedEvent(UUID.randomUUID(), UUID.randomUUID(), 10));
  }

  @Test
  void listenersRunAfterCommitOnGameExecutor() throws Exception {
    var method = GameNotificationEventListener.class.getMethod("onBadge", BadgeEarnedEvent.class);
    assertThat(method.getAnnotation(org.springframework.scheduling.annotation.Async.class).value())
        .isEqualTo("gameTaskExecutor");
    assertThat(method.getAnnotation(org.springframework.transaction.event.TransactionalEventListener.class).phase())
        .isEqualTo(org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT);
  }
}
