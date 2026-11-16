package com.healthcare.kallo.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.healthcare.kallo.event.game.DailyProgressChangedEvent;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class GameEventListenerTest {
  @Test
  void recomputesStreakForChangedDate() {
    StreakService service = Mockito.mock(StreakService.class);
    ChallengeService challenges = Mockito.mock(ChallengeService.class);
    GameEventListener listener = new GameEventListener(service, challenges);
    UUID userId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 11, 13);

    listener.onDailyProgressChanged(new DailyProgressChangedEvent(userId, date));

    verify(service).recompute(userId);
  }

  @Test
  void usesAfterCommitAndGameExecutor() throws Exception {
    var method = GameEventListener.class.getMethod(
        "onDailyProgressChanged", DailyProgressChangedEvent.class);

    var async = method.getAnnotation(org.springframework.scheduling.annotation.Async.class);
    var transactional = method.getAnnotation(
        org.springframework.transaction.event.TransactionalEventListener.class);

    assertThat(async.value()).isEqualTo("gameTaskExecutor");
    assertThat(transactional.phase()).isEqualTo(
        org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT);
    assertThat(transactional.fallbackExecution()).isFalse();
  }

  @Test
  void refreshesChallengeEvenWhenStreakFails() {
    StreakService service = Mockito.mock(StreakService.class);
    ChallengeService challenges = Mockito.mock(ChallengeService.class);
    UUID userId = UUID.randomUUID();
    LocalDate date = LocalDate.now();
    Mockito.doThrow(new IllegalStateException()).when(service).recompute(Mockito.any());

    new GameEventListener(service, challenges).onDailyProgressChanged(
        new DailyProgressChangedEvent(userId, date));

    verify(challenges).refresh(userId, date);
  }
}
