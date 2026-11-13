package com.healthcare.kallo.service.game;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.model.game.Streak;
import com.healthcare.kallo.repository.game.StreakRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;

class StreakMaintenanceJobTest {
  @Test
  void nightlyRecomputeUsesStableUserIdOrderAndContinuesAfterFailure() {
    StreakRepository streaks = mock(StreakRepository.class);
    StreakService service = mock(StreakService.class);
    UUID firstId = UUID.randomUUID();
    UUID secondId = UUID.randomUUID();
    Streak first = streak(firstId);
    Streak second = streak(secondId);

    when(streaks.findByCurrentStreakGreaterThan(0, PageRequest.of(
        0, 500, Sort.by("userId"))))
        .thenReturn(new SliceImpl<>(List.of(first), PageRequest.of(0, 500, Sort.by("userId")), true));
    when(streaks.findByCurrentStreakGreaterThan(0, PageRequest.of(
        1, 500, Sort.by("userId"))))
        .thenReturn(new SliceImpl<>(List.of(second), PageRequest.of(1, 500, Sort.by("userId")), false));
    doThrow(new IllegalStateException("down")).when(service).recompute(firstId);

    new StreakMaintenanceJob(streaks, service).recomputeActiveStreaks();

    verify(service).recompute(firstId);
    verify(service).recompute(secondId);
  }

  private Streak streak(UUID userId) {
    Streak streak = new Streak();
    streak.setUserId(userId);
    streak.setCurrentStreak(1);
    return streak;
  }
}
