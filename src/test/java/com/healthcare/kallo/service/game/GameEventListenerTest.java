package com.healthcare.kallo.service.game;

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
    GameEventListener listener = new GameEventListener(service);
    UUID userId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 11, 13);

    listener.onDailyProgressChanged(new DailyProgressChangedEvent(userId, date));

    verify(service).recompute(userId, date);
  }

  @Test
  void swallowsRecomputeFailure() {
    StreakService service = Mockito.mock(StreakService.class);
    Mockito.doThrow(new IllegalStateException()).when(service).recompute(Mockito.any(), Mockito.any());

    new GameEventListener(service).onDailyProgressChanged(
        new DailyProgressChangedEvent(UUID.randomUUID(), LocalDate.now()));
  }
}
