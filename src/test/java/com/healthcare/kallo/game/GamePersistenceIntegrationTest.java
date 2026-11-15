package com.healthcare.kallo.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.act.ActIntegrationTestBase;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.game.Badge;
import com.healthcare.kallo.model.game.Challenge;
import com.healthcare.kallo.model.game.Friendship;
import com.healthcare.kallo.model.game.PointTransaction;
import com.healthcare.kallo.model.game.Streak;
import com.healthcare.kallo.event.game.DailyProgressChangedEvent;
import com.healthcare.kallo.model.game.UserBadge;
import com.healthcare.kallo.model.game.UserChallenge;
import com.healthcare.kallo.model.game.UserGameSettings;
import com.healthcare.kallo.model.game.enums.ChallengeMetric;
import com.healthcare.kallo.model.game.enums.ChallengeStatus;
import com.healthcare.kallo.model.game.enums.FriendshipStatus;
import com.healthcare.kallo.model.game.enums.PointSource;
import com.healthcare.kallo.repository.game.BadgeRepository;
import com.healthcare.kallo.repository.game.ChallengeRepository;
import com.healthcare.kallo.repository.game.FriendshipRepository;
import com.healthcare.kallo.repository.game.PointTransactionRepository;
import com.healthcare.kallo.repository.game.StreakRepository;
import com.healthcare.kallo.repository.game.UserBadgeRepository;
import com.healthcare.kallo.repository.game.UserChallengeRepository;
import com.healthcare.kallo.repository.game.UserGameSettingsRepository;
import com.healthcare.kallo.service.game.DailyProgress;
import com.healthcare.kallo.service.game.DailyProgressProvider;
import com.healthcare.kallo.service.game.StreakService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

class GamePersistenceIntegrationTest extends ActIntegrationTestBase {
  @Autowired UserGameSettingsRepository settings;
  @Autowired FriendshipRepository friendships;
  @Autowired StreakRepository streaks;
  @Autowired BadgeRepository badges;
  @Autowired UserBadgeRepository userBadges;
  @Autowired ChallengeRepository challenges;
  @Autowired UserChallengeRepository userChallenges;
  @Autowired PointTransactionRepository points;
  @Autowired JdbcTemplate jdbc;
  @Autowired StreakService streakService;
  @Autowired MockMvc mockMvc;
  @Autowired ApplicationEventPublisher events;
  @Autowired TransactionTemplate transactions;
  @Autowired com.healthcare.kallo.service.game.BadgeService badgeService;
  @MockitoBean DailyProgressProvider progress;

  @Test
  void schemaValidatesAndDatabaseConstraintsProtectGameRows() {
    User first = verifiedUser("game-constraints-a");
    User second = verifiedUser("game-constraints-b");

    friendships.saveAndFlush(friendship(first, second));
    assertThatThrownBy(() -> friendships.saveAndFlush(friendship(second, first)))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> friendships.saveAndFlush(friendship(first, first)))
        .isInstanceOf(DataIntegrityViolationException.class);

    settings.saveAndFlush(settings(first, "2345678A"));
    assertThatThrownBy(() -> settings.saveAndFlush(settings(second, "2345678A")))
        .isInstanceOf(DataIntegrityViolationException.class);

    Badge badge = badges.findByCode("FIRST_LOG").orElseThrow();
    UserBadge earned = new UserBadge();
    earned.setUser(first);
    earned.setBadge(badge);
    userBadges.saveAndFlush(earned);
    UserBadge duplicateEarned = new UserBadge();
    duplicateEarned.setUser(first);
    duplicateEarned.setBadge(badge);
    assertThatThrownBy(() -> userBadges.saveAndFlush(duplicateEarned))
        .isInstanceOf(DataIntegrityViolationException.class);

    UUID sourceId = UUID.randomUUID();
    points.saveAndFlush(point(first, sourceId));
    assertThatThrownBy(() -> points.saveAndFlush(point(first, sourceId)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void nativeSettingsInsertTreatsAnyConflictAsMissingInsert() {
    User first = verifiedUser("game-settings-a");
    User second = verifiedUser("game-settings-b");

    assertThat(settings.insertIfAbsent(first.getId(), "2345678B")).isEqualTo(1);
    assertThat(settings.insertIfAbsent(second.getId(), "2345678B")).isZero();
    assertThat(settings.findById(second.getId())).isEmpty();
  }

  @Test
  void streakUpsertAcceptsNullLastCompletedDate() {
    User user = verifiedUser("game-streak-null");

    assertThat(streaks.upsert(user.getId(), 0, 0, null)).isEqualTo(1);

    assertThat(streaks.findById(user.getId()).orElseThrow().getLastCompletedDate()).isNull();
  }

  @Test
  void recomputePersistsStreakAndExposesItThroughAuthenticatedApi() throws Exception {
    User user = verifiedUser("game-streak-api");
    LocalDate today = LocalDate.now(ZoneId.of(user.getTimezone()));
    when(progress.findRange(any(), any(), any())).thenReturn(List.of(
        new DailyProgress(today.minusDays(1), 2, 2_000, 2_000, 0, null, 0, null),
        new DailyProgress(today, 2, 2_000, 2_000, 0, null, 0, null)));

    streakService.recompute(user.getId());

    Streak streak = streaks.findById(user.getId()).orElseThrow();
    assertThat(streak.getCurrentStreak()).isEqualTo(2);
    assertThat(streak.getLongestStreak()).isEqualTo(2);
    mockMvc.perform(get("/api/streak").header("Authorization", "Bearer " + accessToken(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.currentStreak").value(2))
        .andExpect(jsonPath("$.data.todayCompleted").value(true));
  }

  @Test
  void streakBadgeIsEarnedAfterSevenDayRecomputeAndKeepsOnePointLedgerRow() {
    User user = verifiedUser("game-badge-streak-7");
    LocalDate today = LocalDate.now(ZoneId.of(user.getTimezone()));
    streaks.upsert(user.getId(), 6, 6, today.minusDays(6));
    when(progress.findRange(any(), any(), any())).thenReturn(java.util.stream.IntStream.rangeClosed(0, 6)
        .mapToObj(offset -> new DailyProgress(today.minusDays(offset), 2, 2_000, 2_000, 0, null, 0, null))
        .toList());

    streakService.recompute(user.getId());
    streakService.recompute(user.getId());

    Badge badge = badges.findByCode("STREAK_7").orElseThrow();
    assertThat(jdbc.queryForObject(
        "select count(*) from user_badges where user_id = ? and badge_id = ?", Long.class,
        user.getId(), badge.getId())).isOne();
    assertThat(jdbc.queryForObject(
        "select count(*) from point_transactions where user_id = ? and source_type = ? and source_id = ?",
        Long.class, user.getId(), PointSource.BADGE.name(), badge.getId())).isOne();
  }

  @Test
  void earnedStreakBadgeRemainsAfterStreakBreaks() {
    User user = verifiedUser("game-badge-retained");
    Badge badge = badges.findByCode("STREAK_7").orElseThrow();
    UserBadge earned = new UserBadge();
    earned.setUser(user);
    earned.setBadge(badge);
    userBadges.saveAndFlush(earned);

    when(progress.findRange(any(), any(), any())).thenReturn(List.of());
    streakService.recompute(user.getId());

    assertThat(jdbc.queryForObject(
        "select count(*) from user_badges where user_id = ? and badge_id = ?", Long.class,
        user.getId(), badge.getId())).isOne();
  }

  @Test
  void concurrentBadgeGrantsCreateOneBadgeAndOnePoint() throws Exception {
    User user = verifiedUser("game-badge-concurrent");
    Badge badge = badges.findByCode("FIRST_LOG").orElseThrow();
    int workers = 2;
    CountDownLatch ready = new CountDownLatch(workers);
    CountDownLatch start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(workers);
    try {
      var futures = java.util.stream.IntStream.range(0, workers)
          .mapToObj(index -> executor.submit(() -> {
            ready.countDown();
            start.await();
            badgeService.grant(user.getId(), badge.getId());
            return null;
          }))
          .toList();
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      futures.forEach(future -> {
        try {
          future.get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
          throw new AssertionError(exception);
        }
      });
    } finally {
      executor.shutdownNow();
    }

    assertThat(jdbc.queryForObject(
        "select count(*) from user_badges where user_id = ? and badge_id = ?", Long.class,
        user.getId(), badge.getId())).isOne();
    assertThat(jdbc.queryForObject(
        "select count(*) from point_transactions where user_id = ? and source_type = ? and source_id = ?",
        Long.class, user.getId(), PointSource.BADGE.name(), badge.getId())).isOne();
  }

  @Test
  void firstFriendBadgeIsEarnedAfterAcceptance() throws Exception {
    User requester = verifiedUser("game-friend-requester");
    User addressee = verifiedUser("game-friend-addressee");
    Friendship friendship = friendship(requester, addressee);
    friendship = friendships.saveAndFlush(friendship);

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
            "/api/friends/requests/" + friendship.getId())
        .header("Authorization", "Bearer " + accessToken(addressee))
        .contentType("application/json")
        .content("{\"action\":\"ACCEPT\"}"))
        .andExpect(status().isOk());

    awaitBadge(requester.getId(), "FIRST_FRIEND");
    awaitBadge(addressee.getId(), "FIRST_FRIEND");
  }

  @Test
  void inactiveBadgeCannotBeGranted() {
    User user = verifiedUser("game-badge-inactive");
    Badge badge = badges.findByCode("WEIGHT_GOAL_REACHED").orElseThrow();

    assertThat(badgeService.grant(user.getId(), badge.getId())).isFalse();
    assertThat(jdbc.queryForObject(
        "select count(*) from user_badges where user_id = ? and badge_id = ?", Long.class,
        user.getId(), badge.getId())).isZero();
  }

  @Test
  void badgeListMarksEarnedBadgesAndExcludesInactiveBadges() throws Exception {
    User user = verifiedUser("game-badge-api");
    Badge badge = badges.findByCode("FIRST_LOG").orElseThrow();
    UserBadge earned = new UserBadge();
    earned.setUser(user);
    earned.setBadge(badge);
    userBadges.saveAndFlush(earned);

    mockMvc.perform(get("/api/badges").header("Authorization", "Bearer " + accessToken(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(7))
        .andExpect(jsonPath("$.data[0].code").value("FIRST_LOG"))
        .andExpect(jsonPath("$.data[0].earned").value(true))
        .andExpect(jsonPath("$.data[1].code").value("STREAK_7"))
        .andExpect(jsonPath("$.data[2].code").value("STREAK_30"))
        .andExpect(jsonPath("$.data[3].code").value("STREAK_100"))
        .andExpect(jsonPath("$.data[4].code").value("FIRST_FRIEND"))
        .andExpect(jsonPath("$.data[5].code").value("CHALLENGER_1"))
        .andExpect(jsonPath("$.data[6].code").value("CHALLENGER_5"));
  }

  @Test
  void recomputeIsIdempotentAndHandlesNullProviderData() {
    User user = verifiedUser("game-streak-idempotent");
    when(progress.findRange(any(), any(), any())).thenReturn(List.of());

    streakService.recompute(user.getId());
    streakService.recompute(user.getId());

    assertThat(count("streaks", user.getId())).isOne();
    Streak streak = streaks.findById(user.getId()).orElseThrow();
    assertThat(streak.getCurrentStreak()).isZero();
    assertThat(streak.getLongestStreak()).isZero();
    assertThat(streak.getLastCompletedDate()).isNull();
  }

  @Test
  void firstConcurrentRecomputesCreateOneValidStreakRow() throws Exception {
    User user = verifiedUser("game-streak-concurrent");
    LocalDate today = LocalDate.now(ZoneId.of(user.getTimezone()));
    List<DailyProgress> data = List.of(
        new DailyProgress(today.minusDays(1), 2, 2_000, 2_000, 0, null, 0, null),
        new DailyProgress(today, 2, 2_000, 2_000, 0, null, 0, null));
    when(progress.findRange(any(), any(), any())).thenReturn(data);

    int workers = 2;
    CountDownLatch ready = new CountDownLatch(workers);
    CountDownLatch start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(workers);
    try {
      var futures = List.of(
          executor.submit(() -> recomputeAtBarrier(user.getId(), ready, start)),
          executor.submit(() -> recomputeAtBarrier(user.getId(), ready, start)));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      futures.forEach(future -> {
        try {
          future.get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
          throw new AssertionError(exception);
        }
      });
    } finally {
      executor.shutdownNow();
    }

    assertThat(count("streaks", user.getId())).isOne();
    assertThat(streaks.findById(user.getId()).orElseThrow().getCurrentStreak()).isEqualTo(2);
  }

  @Test
  void committedProgressEventCreatesStreakButRollbackDoesNot() throws Exception {
    User user = verifiedUser("game-streak-event");
    LocalDate today = LocalDate.now(ZoneId.of(user.getTimezone()));
    when(progress.findRange(any(), any(), any())).thenReturn(List.of(
        new DailyProgress(today, 2, 2_000, 2_000, 0, null, 0, null)));

    transactions.executeWithoutResult(status ->
        events.publishEvent(new DailyProgressChangedEvent(user.getId(), today)));
    awaitStreakRow(user.getId());
    assertThat(streaks.findById(user.getId())).isPresent();

    User rolledBack = verifiedUser("game-streak-event-rollback");
    transactions.executeWithoutResult(status -> {
      events.publishEvent(new DailyProgressChangedEvent(rolledBack.getId(), today));
      status.setRollbackOnly();
    });
    assertThat(streaks.findById(rolledBack.getId())).isEmpty();
  }

  @Test
  void hourlyResetClearsExpiredRowsAndLeavesCurrentRowsUntouched() {
    User expiredUser = verifiedUser("game-streak-reset-expired");
    User currentUser = verifiedUser("game-streak-reset-current");
    LocalDate today = databaseToday(expiredUser.getTimezone());

    Streak expired = new Streak();
    expired.setUserId(expiredUser.getId());
    expired.setCurrentStreak(4);
    expired.setLongestStreak(7);
    expired.setLastCompletedDate(today.minusDays(2));
    streaks.saveAndFlush(expired);

    Streak current = new Streak();
    current.setUserId(currentUser.getId());
    current.setCurrentStreak(3);
    current.setLongestStreak(5);
    current.setLastCompletedDate(today);
    streaks.saveAndFlush(current);

    assertThat(streaks.resetExpiredCurrentStreaks()).isEqualTo(1);
    assertThat(streaks.findById(expiredUser.getId()).orElseThrow().getCurrentStreak()).isZero();
    assertThat(streaks.findById(currentUser.getId()).orElseThrow().getCurrentStreak()).isEqualTo(3);
  }

  @Test
  void deletingUserCascadesAllGameRowsButKeepsSharedDefinitions() {
    User first = verifiedUser("game-cascade-a");
    User second = verifiedUser("game-cascade-b");
    Badge badge = badges.findByCode("FIRST_LOG").orElseThrow();

    settings.saveAndFlush(settings(first, "2345678C"));
    friendships.saveAndFlush(friendship(first, second));

    Streak streak = new Streak();
    streak.setUserId(first.getId());
    streak.setCurrentStreak(1);
    streak.setLongestStreak(1);
    streak.setLastCompletedDate(LocalDate.now());
    streaks.saveAndFlush(streak);

    UserBadge userBadge = new UserBadge();
    userBadge.setUser(first);
    userBadge.setBadge(badge);
    userBadges.saveAndFlush(userBadge);

    Challenge challenge = new Challenge();
    challenge.setTitle("Cascade challenge");
    challenge.setDescription("Cascade test");
    challenge.setMetric(ChallengeMetric.LOG_DAYS);
    challenge.setTargetValue(1);
    challenge.setStartDate(LocalDate.now().minusDays(1));
    challenge.setEndDate(LocalDate.now().plusDays(1));
    challenge.setPointsReward(1);
    challenge.setBadge(badge);
    challenges.saveAndFlush(challenge);

    UserChallenge userChallenge = new UserChallenge();
    userChallenge.setUser(first);
    userChallenge.setChallenge(challenge);
    userChallenge.setJoinedLocalDate(LocalDate.now());
    userChallenge.setStatus(ChallengeStatus.ACTIVE);
    userChallenges.saveAndFlush(userChallenge);
    points.saveAndFlush(point(first, UUID.randomUUID()));

    users.deleteById(first.getId());

    assertThat(count("user_game_settings", first.getId())).isZero();
    assertThat(jdbc.queryForObject(
            "select count(*) from friendships where requester_id = ? or addressee_id = ?",
            Long.class, first.getId(), first.getId()))
        .isZero();
    assertThat(count("streaks", first.getId())).isZero();
    assertThat(count("user_badges", first.getId())).isZero();
    assertThat(count("user_challenges", first.getId())).isZero();
    assertThat(count("point_transactions", first.getId())).isZero();
    assertThat(users.findById(second.getId())).isPresent();
    assertThat(challenges.findById(challenge.getId())).isPresent();
    assertThat(badges.findById(badge.getId())).isPresent();
  }

  private LocalDate databaseToday(String timezone) {
    return jdbc.queryForObject(
        "select (now() at time zone ?)::date", LocalDate.class, timezone);
  }

  private void recomputeAtBarrier(UUID userId, CountDownLatch ready, CountDownLatch start) {
    ready.countDown();
    try {
      start.await();
      streakService.recompute(userId);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }

  private void awaitStreakRow(UUID userId) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (streaks.findById(userId).isEmpty() && System.nanoTime() < deadline) {
      Thread.sleep(25);
    }
  }

  private Friendship friendship(User requester, User addressee) {
    Friendship friendship = new Friendship();
    friendship.setRequester(requester);
    friendship.setAddressee(addressee);
    friendship.setStatus(FriendshipStatus.PENDING);
    return friendship;
  }

  private UserGameSettings settings(User user, String friendCode) {
    UserGameSettings settings = new UserGameSettings();
    settings.setUserId(user.getId());
    settings.setFriendCode(friendCode);
    return settings;
  }

  private PointTransaction point(User user, UUID sourceId) {
    PointTransaction point = new PointTransaction();
    point.setUser(user);
    point.setPoints(10);
    point.setSourceType(PointSource.BADGE);
    point.setSourceId(sourceId);
    return point;
  }

  private void awaitBadge(UUID userId, String code) throws InterruptedException {
    UUID badgeId = badges.findByCode(code).orElseThrow().getId();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (jdbc.queryForObject(
            "select count(*) from user_badges where user_id = ? and badge_id = ?",
            Long.class, userId, badgeId) == 0 && System.nanoTime() < deadline) {
      Thread.sleep(25);
    }
    assertThat(jdbc.queryForObject(
        "select count(*) from user_badges where user_id = ? and badge_id = ?",
        Long.class, userId, badgeId)).isOne();
  }

  private long count(String table, UUID userId) {
    return jdbc.queryForObject("select count(*) from " + table + " where user_id = ?", Long.class, userId);
  }
}
