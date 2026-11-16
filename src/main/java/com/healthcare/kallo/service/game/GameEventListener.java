package com.healthcare.kallo.service.game;

import com.healthcare.kallo.event.game.DailyProgressChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class GameEventListener {
  private static final Logger log = LoggerFactory.getLogger(GameEventListener.class);
  private final StreakService streaks;
  private final ChallengeService challenges;

  public GameEventListener(StreakService streaks, ChallengeService challenges) {
    this.streaks = streaks;
    this.challenges = challenges;
  }

  @Async("gameTaskExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDailyProgressChanged(DailyProgressChangedEvent event) {
    try {
      streaks.recompute(event.userId());
    } catch (RuntimeException exception) {
      log.warn("GAME streak recompute failed for user {}", event.userId(), exception);
    }
    try {
      challenges.refresh(event.userId(), event.localDate());
    } catch (RuntimeException exception) {
      log.warn("GAME challenge refresh failed for user {}", event.userId(), exception);
    }
  }
}
