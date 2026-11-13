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

  public GameEventListener(StreakService streaks) {
    this.streaks = streaks;
  }

  @Async("gameTaskExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onDailyProgressChanged(DailyProgressChangedEvent event) {
    try {
      streaks.recompute(event.userId(), event.localDate());
    } catch (RuntimeException exception) {
      log.warn("GAME streak recompute failed for user {}", event.userId(), exception);
    }
  }
}
