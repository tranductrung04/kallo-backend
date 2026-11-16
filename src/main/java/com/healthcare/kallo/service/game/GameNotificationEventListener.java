package com.healthcare.kallo.service.game;

import com.healthcare.kallo.event.game.BadgeEarnedEvent;
import com.healthcare.kallo.event.game.ChallengeCompletedEvent;
import com.healthcare.kallo.event.game.FriendRequestAcceptedEvent;
import com.healthcare.kallo.event.game.FriendRequestReceivedEvent;
import com.healthcare.kallo.service.sys.RealtimePublisher;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class GameNotificationEventListener {
  private static final Logger log = LoggerFactory.getLogger(GameNotificationEventListener.class);
  private final GameNotifier notifier;
  private final RealtimePublisher realtime;

  public GameNotificationEventListener(GameNotifier notifier, RealtimePublisher realtime) {
    this.notifier = notifier;
    this.realtime = realtime;
  }

  @Async("gameTaskExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onBadge(BadgeEarnedEvent event) {
    safely(() -> notifier.badgeEarned(event));
  }

  @Async("gameTaskExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onChallenge(ChallengeCompletedEvent event) {
    safely(() -> notifier.challengeCompleted(event));
  }

  @Async("gameTaskExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onFriendRequest(FriendRequestReceivedEvent event) {
    safely(() -> realtime.sendToUser(event.recipientId(), "/queue/game-events", "FRIEND_REQUEST_RECEIVED",
        Map.of("requestId", event.requestId(), "user", Map.of("id", event.requesterId()))));
  }

  @Async("gameTaskExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onFriendAccepted(FriendRequestAcceptedEvent event) {
    safely(() -> realtime.sendToUser(event.recipientId(), "/queue/game-events", "FRIEND_REQUEST_ACCEPTED",
        Map.of("user", Map.of("id", event.friendId()))));
  }

  private void safely(Runnable action) {
    try { action.run(); } catch (RuntimeException exception) {
      log.warn("GAME notification failed", exception);
    }
  }
}
