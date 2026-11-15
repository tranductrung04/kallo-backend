package com.healthcare.kallo.service.game;

import com.healthcare.kallo.event.game.FriendAcceptedEvent;
import com.healthcare.kallo.model.game.enums.BadgeRuleType;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class FriendBadgeEventListener {
  private static final Logger log = LoggerFactory.getLogger(FriendBadgeEventListener.class);
  private final BadgeService badges;

  public FriendBadgeEventListener(BadgeService badges) {
    this.badges = badges;
  }

  @Async("gameTaskExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onFriendAccepted(FriendAcceptedEvent event) {
    try {
      badges.evaluate(event.userId(), Set.of(BadgeRuleType.FRIENDS_COUNT));
    } catch (RuntimeException exception) {
      log.warn("GAME friend badge evaluation failed for user {}", event.userId(), exception);
    }
  }
}
