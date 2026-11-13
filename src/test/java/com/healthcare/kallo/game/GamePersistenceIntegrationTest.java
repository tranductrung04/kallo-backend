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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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

    streakService.recompute(user.getId(), today);

    Streak streak = streaks.findById(user.getId()).orElseThrow();
    assertThat(streak.getCurrentStreak()).isEqualTo(2);
    assertThat(streak.getLongestStreak()).isEqualTo(2);
    mockMvc.perform(get("/api/streak").header("Authorization", "Bearer " + accessToken(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.currentStreak").value(2))
        .andExpect(jsonPath("$.data.todayCompleted").value(true));
  }

  @Test
  void hourlyResetClearsOnlyExpiredCurrentStreak() {
    User user = verifiedUser("game-streak-reset");
    Streak streak = new Streak();
    streak.setUserId(user.getId());
    streak.setCurrentStreak(4);
    streak.setLongestStreak(7);
    streak.setLastCompletedDate(LocalDate.now(ZoneId.of(user.getTimezone())).minusDays(2));
    streaks.saveAndFlush(streak);

    assertThat(streaks.resetExpiredCurrentStreaks()).isEqualTo(1);

    Streak reset = streaks.findById(user.getId()).orElseThrow();
    assertThat(reset.getCurrentStreak()).isZero();
    assertThat(reset.getLongestStreak()).isEqualTo(7);
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

  private long count(String table, UUID userId) {
    return jdbc.queryForObject("select count(*) from " + table + " where user_id = ?", Long.class, userId);
  }
}
