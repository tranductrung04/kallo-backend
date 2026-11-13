package com.healthcare.kallo.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.healthcare.kallo.config.GameProperties;
import com.healthcare.kallo.dto.response.game.StreakResponse;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.game.Streak;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.game.StreakRepository;
import com.healthcare.kallo.service.game.impl.StreakServiceImpl;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StreakServiceImplTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);

  @Mock StreakRepository streaks;
  @Mock UserRepository users;
  @Mock DailyProgressProvider progress;
  private StreakServiceImpl service;
  private User user;

  @BeforeEach
  void setUp() {
    user = new User();
    user.setId(USER_ID);
    user.setTimezone("Asia/Ho_Chi_Minh");
    when(users.findById(USER_ID)).thenReturn(Optional.of(user));
    var clock = Clock.fixed(Instant.parse("2026-11-13T17:00:00Z"), ZoneOffset.UTC);
    var resolver = new UserDayResolver(clock);
    var properties = new GameProperties(
        new GameProperties.DailyGoal(2, 10),
        new GameProperties.Streak(400),
        new GameProperties.Leaderboard(java.time.ZoneId.of("Asia/Ho_Chi_Minh"), false, java.time.Duration.ofMillis(500)),
        new GameProperties.Friend(200, 50, java.time.Duration.ofHours(24), 20, java.time.Duration.ofHours(1)),
        new GameProperties.Search(3, 20),
        new GameProperties.Challenge(java.time.Duration.ofDays(1)),
        new GameProperties.Ws(3, List.of()));
    service = new StreakServiceImpl(streaks, users, progress, new DailyGoalPolicy(2, 10), resolver,
        properties, new StreakCalculator());
  }

  @Test
  void recomputeUsesConfiguredWindowAndPersistsCalculatedResult() {
    Streak existing = new Streak();
    existing.setUserId(USER_ID);
    existing.setLongestStreak(2);
    when(streaks.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(existing));
    when(progress.findRange(eq(USER_ID), eq(TODAY.minusDays(399)), eq(TODAY)))
        .thenReturn(List.of(
            new DailyProgress(TODAY.minusDays(2), 2, 2_000, 2_000, 0, null, 0, null),
            new DailyProgress(TODAY.minusDays(1), 2, 2_000, 2_000, 0, null, 0, null),
            new DailyProgress(TODAY, 2, 2_000, 2_000, 0, null, 0, null)));

    service.recompute(USER_ID, TODAY);

    verify(streaks).upsert(USER_ID, 3, 3, TODAY);
  }

  @Test
  void getLazilyResetsStaleCurrentStreakWithoutWriting() {
    Streak stale = new Streak();
    stale.setUserId(USER_ID);
    stale.setCurrentStreak(7);
    stale.setLongestStreak(9);
    stale.setLastCompletedDate(TODAY.minusDays(2));
    when(streaks.findById(USER_ID)).thenReturn(Optional.of(stale));

    StreakResponse response = service.get(USER_ID);

    assertThat(response).isEqualTo(new StreakResponse(0, 9, TODAY.minusDays(2), false, false));
    verify(streaks, never()).upsert(any(), anyInt(), anyInt(), any());
  }

  @Test
  void getReportsYesterdayAsAtRiskAndMissingRowAsZero() {
    Streak atRisk = new Streak();
    atRisk.setUserId(USER_ID);
    atRisk.setCurrentStreak(3);
    atRisk.setLongestStreak(5);
    atRisk.setLastCompletedDate(TODAY.minusDays(1));
    when(streaks.findById(USER_ID)).thenReturn(Optional.of(atRisk));

    assertThat(service.get(USER_ID))
        .isEqualTo(new StreakResponse(3, 5, TODAY.minusDays(1), false, true));

    when(streaks.findById(USER_ID)).thenReturn(Optional.empty());
    assertThat(service.get(USER_ID)).isEqualTo(new StreakResponse(0, 0, null, false, false));
  }
}
