package com.healthcare.kallo.service.game.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.GameProperties;
import com.healthcare.kallo.dto.response.game.LeaderboardEntryResponse;
import com.healthcare.kallo.dto.response.game.LeaderboardResponse;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.auth.enums.UserStatus;
import com.healthcare.kallo.model.game.Streak;
import com.healthcare.kallo.model.game.UserGameSettings;
import com.healthcare.kallo.model.game.enums.LeaderboardMetric;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.game.FriendshipRepository;
import com.healthcare.kallo.repository.game.PointTransactionRepository;
import com.healthcare.kallo.repository.game.StreakRepository;
import com.healthcare.kallo.repository.game.UserGameSettingsRepository;
import com.healthcare.kallo.service.auth.StorageService;
import com.healthcare.kallo.service.game.DailyProgressProvider;
import com.healthcare.kallo.service.game.LeaderboardService;
import com.healthcare.kallo.service.game.UserDayResolver;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaderboardServiceImpl implements LeaderboardService {
  private static final int MAX_ENTRIES = 50;
  private static final java.time.Duration AVATAR_EXPIRATION = java.time.Duration.ofMinutes(15);

  private final FriendshipRepository friendships;
  private final UserRepository users;
  private final UserGameSettingsRepository settings;
  private final StreakRepository streaks;
  private final PointTransactionRepository points;
  private final DailyProgressProvider progress;
  private final StorageService storage;
  private final GameProperties properties;
  private final Clock clock;
  private final UserDayResolver days;

  public LeaderboardServiceImpl(
      FriendshipRepository friendships,
      UserRepository users,
      UserGameSettingsRepository settings,
      StreakRepository streaks,
      PointTransactionRepository points,
      DailyProgressProvider progress,
      StorageService storage,
      GameProperties properties,
      Clock clock,
      UserDayResolver days) {
    this.friendships = friendships;
    this.users = users;
    this.settings = settings;
    this.streaks = streaks;
    this.points = points;
    this.progress = progress;
    this.storage = storage;
    this.properties = properties;
    this.clock = clock;
    this.days = days;
  }

  @Override
  @Transactional(readOnly = true)
  public long valueOf(UUID userId, LeaderboardMetric metric) {
    if (metric == LeaderboardMetric.STEPS && !properties.leaderboard().stepsEnabled()) {
      throw new BusinessException(ErrorCode.INVALID_PARAMETER);
    }
    User user = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    return values(Map.of(userId, user), metric, period()).getOrDefault(userId, 0L);
  }

  @Override
  @Transactional(readOnly = true)
  public LeaderboardResponse get(UUID userId, LeaderboardMetric metric) {
    if (metric == LeaderboardMetric.STEPS && !properties.leaderboard().stepsEnabled()) {
      throw new BusinessException(ErrorCode.INVALID_PARAMETER);
    }
    User current = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    if (current.getStatus() != UserStatus.ACTIVE) throw new BusinessException(ErrorCode.NOT_FOUND);
    Set<UUID> ids = new java.util.HashSet<>(friendships.findAcceptedFriendIds(userId));
    ids.add(userId);

    Map<UUID, User> usersById = users.findByIdIn(ids).stream()
        .filter(user -> user.getStatus() == UserStatus.ACTIVE)
        .collect(Collectors.toMap(User::getId, Function.identity()));
    Map<UUID, UserGameSettings> settingsById = settings.findByUserIdIn(usersById.keySet()).stream()
        .collect(Collectors.toMap(UserGameSettings::getUserId, Function.identity()));
    usersById.keySet().removeIf(id -> !id.equals(userId) && !shares(settingsById.get(id), metric));
    usersById.putIfAbsent(userId, current);

    Period period = period();
    Map<UUID, Long> values = values(usersById, metric, period);
    List<LeaderboardEntryResponse> ranked = usersById.values().stream()
        .map(user -> new ScoredUser(user, values.getOrDefault(user.getId(), 0L)))
        .sorted(Comparator.comparingLong(ScoredUser::value).reversed()
            .thenComparing(value -> value.user().getDisplayName(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(value -> value.user().getId()))
        .map(new java.util.function.Function<ScoredUser, LeaderboardEntryResponse>() {
          private long previous = Long.MIN_VALUE;
          private long rank;
          private long index;

          @Override
          public LeaderboardEntryResponse apply(ScoredUser scored) {
            index++;
            if (scored.value() != previous) rank = index;
            previous = scored.value();
            return new LeaderboardEntryResponse(scored.user().getId(), scored.user().getDisplayName(),
                avatar(scored.user().getAvatarUrl()), rank, scored.value());
          }
        }).toList();
    LeaderboardEntryResponse me = ranked.stream().filter(entry -> entry.userId().equals(userId)).findFirst()
        .orElseThrow();
    return new LeaderboardResponse(metric,
        new LeaderboardResponse.Period(period.from(), period.to()), me, ranked.stream().limit(MAX_ENTRIES).toList());
  }

  private Map<UUID, Long> values(Map<UUID, User> usersById, LeaderboardMetric metric, Period period) {
    Set<UUID> ids = usersById.keySet();
    if (metric == LeaderboardMetric.POINTS) {
      return points.sumPoints(List.copyOf(ids), period.from(), period.to()).stream()
          .collect(Collectors.toMap(PointTransactionRepository.PointSum::getUserId,
              value -> value.getPoints() == null ? 0L : value.getPoints()));
    }
    if (metric == LeaderboardMetric.STEPS) {
      LocalDate from = period.from().atZone(properties.leaderboard().zone()).toLocalDate();
      LocalDate to = period.to().minusNanos(1).atZone(properties.leaderboard().zone()).toLocalDate();
      return ids.stream().collect(Collectors.toMap(Function.identity(),
          id -> Math.max(0, progress.sumSteps(id, from, to))));
    }
    Map<UUID, Streak> rows = streaks.findAllById(ids).stream()
        .collect(Collectors.toMap(Streak::getUserId, Function.identity()));
    return ids.stream().collect(Collectors.toMap(Function.identity(), id -> {
      Streak streak = rows.get(id);
      if (streak == null || streak.getLastCompletedDate() == null) return 0L;
      LocalDate today = days.today(usersById.get(id));
      return today.minusDays(1).equals(streak.getLastCompletedDate())
              || today.equals(streak.getLastCompletedDate())
          ? streak.getCurrentStreak() : 0L;
    }));
  }


  private Period period() {
    ZoneId zone = properties.leaderboard().zone();
    ZonedDateTime now = clock.instant().atZone(zone);
    ZonedDateTime from = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate()
        .atStartOfDay(zone);
    return new Period(from.toInstant(), from.plusWeeks(1).toInstant());
  }

  private boolean shares(UserGameSettings setting, LeaderboardMetric metric) {
    if (setting == null) return metric != LeaderboardMetric.STEPS;
    return switch (metric) {
      case STREAK -> setting.isShareStreak();
      case POINTS -> setting.isSharePoints();
      case STEPS -> setting.isShareSteps();
    };
  }

  private String avatar(String key) {
    return key == null ? null : storage.presignedUrl(key, AVATAR_EXPIRATION);
  }

  private record Period(Instant from, Instant to) {}
  private record ScoredUser(User user, long value) {}
}
