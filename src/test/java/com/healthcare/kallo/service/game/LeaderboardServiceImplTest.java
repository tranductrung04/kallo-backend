package com.healthcare.kallo.service.game;

import com.healthcare.kallo.config.GameProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.dto.response.game.LeaderboardResponse;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.game.UserGameSettings;
import com.healthcare.kallo.model.game.enums.LeaderboardMetric;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.game.FriendshipRepository;
import com.healthcare.kallo.repository.game.PointTransactionRepository;
import com.healthcare.kallo.repository.game.StreakRepository;
import com.healthcare.kallo.repository.game.UserGameSettingsRepository;
import com.healthcare.kallo.service.auth.StorageService;
import com.healthcare.kallo.service.game.impl.LeaderboardServiceImpl;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static com.healthcare.kallo.config.GamePropertiesFixture.defaults;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LeaderboardServiceImplTest {
  private static final UUID ME = UUID.randomUUID();
  private static final UUID FRIEND = UUID.randomUUID();

  private final FriendshipRepository friendships = mock(FriendshipRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final UserGameSettingsRepository settings = mock(UserGameSettingsRepository.class);
  private final StreakRepository streaks = mock(StreakRepository.class);
  private final PointTransactionRepository points = mock(PointTransactionRepository.class);
  private final DailyProgressProvider progress = mock(DailyProgressProvider.class);
  private final StorageService storage = mock(StorageService.class);
  private final User me = user(ME, "Me");
  private final User friend = user(FRIEND, "Friend");
  private LeaderboardService service;

  @BeforeEach
  void setUp() {
    GameProperties properties = defaults();
    properties = new GameProperties(properties.dailyGoal(), properties.streak(),
        new GameProperties.Leaderboard(properties.leaderboard().zone(), false, Duration.ofMillis(1)),
        properties.friend(), properties.search(), properties.challenge(), properties.ws());
    service = new LeaderboardServiceImpl(friendships, users, settings, streaks, points, progress, storage,
        properties, Clock.fixed(Instant.parse("2026-11-16T03:00:00Z"), ZoneId.of("UTC")),
        new UserDayResolver(Clock.fixed(Instant.parse("2026-11-16T03:00:00Z"), ZoneId.of("UTC"))));
    when(users.findById(ME)).thenReturn(Optional.of(me));
    when(friendships.findAcceptedFriendIds(ME)).thenReturn(List.of(FRIEND));
    when(users.findByIdIn(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(me, friend));
    when(settings.findByUserIdIn(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
        settings(ME, true), settings(FRIEND, true)));
    var mePoints = sum(ME, 20);
    var friendPoints = sum(FRIEND, 20);
    when(points.sumPoints(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any())).thenReturn(List.of(mePoints, friendPoints));
  }

  @Test
  void ranksTiesAndUsesConfiguredMondayPeriod() {
    LeaderboardResponse response = service.get(ME, LeaderboardMetric.POINTS);

    assertThat(response.period().from()).isEqualTo(Instant.parse("2026-11-15T17:00:00Z"));
    assertThat(response.period().to()).isEqualTo(Instant.parse("2026-11-22T17:00:00Z"));
    assertThat(response.entries()).extracting("rank").containsExactly(1L, 1L);
    assertThat(response.me().value()).isEqualTo(20);
  }

  @Test
  void excludesFriendWhoDisabledPointSharing() {
    when(settings.findByUserIdIn(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
        settings(ME, true), settings(FRIEND, false)));

    LeaderboardResponse response = service.get(ME, LeaderboardMetric.POINTS);

    assertThat(response.entries()).extracting("userId").containsExactly(ME);
  }

  private static User user(UUID id, String name) {
    User user = new User();
    user.setId(id);
    user.setDisplayName(name);
    user.setStatus(com.healthcare.kallo.model.auth.enums.UserStatus.ACTIVE);
    user.setTimezone("Asia/Ho_Chi_Minh");
    return user;
  }

  private static UserGameSettings settings(UUID userId, boolean sharePoints) {
    UserGameSettings settings = new UserGameSettings();
    settings.setUserId(userId);
    settings.setSharePoints(sharePoints);
    return settings;
  }

  private static PointTransactionRepository.PointSum sum(UUID userId, long value) {
    PointTransactionRepository.PointSum sum = mock(PointTransactionRepository.PointSum.class);
    when(sum.getUserId()).thenReturn(userId);
    when(sum.getPoints()).thenReturn(value);
    return sum;
  }
}
