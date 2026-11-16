package com.healthcare.kallo.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.event.game.ChallengeCompletedEvent;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.game.Challenge;
import com.healthcare.kallo.model.game.UserChallenge;
import com.healthcare.kallo.model.game.enums.ChallengeMetric;
import com.healthcare.kallo.model.game.enums.ChallengeStatus;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.game.ChallengeRepository;
import com.healthcare.kallo.repository.game.UserChallengeRepository;
import com.healthcare.kallo.service.game.GameAuditLogger;
import com.healthcare.kallo.service.game.impl.ChallengeServiceImpl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static com.healthcare.kallo.config.GamePropertiesFixture.defaults;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ChallengeServiceImplTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID CHALLENGE_ID = UUID.randomUUID();
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);

  @Mock ChallengeRepository challenges;
  @Mock UserChallengeRepository userChallenges;
  @Mock UserRepository users;
  @Mock DailyProgressProvider progress;
  @Mock PointService points;
  @Mock BadgeService badges;
  @Mock ApplicationEventPublisher events;

  private ChallengeServiceImpl service;
  private User user;
  private Challenge challenge;
  private UserChallenge participation;

  @BeforeEach
  void setUp() {
    user = new User();
    user.setId(USER_ID);
    user.setTimezone("Asia/Ho_Chi_Minh");
    when(users.findById(USER_ID)).thenReturn(Optional.of(user));
    var properties = defaults();
    var clock = Clock.fixed(Instant.parse("2026-11-17T05:00:00Z"), ZoneOffset.UTC);
    service = new ChallengeServiceImpl(challenges, userChallenges, users, progress,
        new DailyGoalPolicy(2, 10), new UserDayResolver(clock), points, badges, events,
        org.mockito.Mockito.mock(GameAuditLogger.class), clock);

    challenge = new Challenge();
    challenge.setId(CHALLENGE_ID);
    challenge.setTitle("Water week");
    challenge.setDescription("Drink water");
    challenge.setMetric(ChallengeMetric.WATER_GOAL_DAYS);
    challenge.setTargetValue(5);
    challenge.setStartDate(TODAY.minusDays(6));
    challenge.setEndDate(TODAY.plusDays(3));
    challenge.setPointsReward(20);
    challenge.setActive(true);

    participation = new UserChallenge();
    participation.setId(UUID.randomUUID());
    participation.setUser(user);
    participation.setChallenge(challenge);
    participation.setJoinedLocalDate(TODAY.minusDays(4));
    participation.setStatus(ChallengeStatus.ACTIVE);
  }

  @Test
  void backfilledEventRecomputesThroughTodayAndNeverRollsProgressBack() {
    when(userChallenges.findActiveForDate(USER_ID, TODAY.minusDays(2), ChallengeStatus.ACTIVE))
        .thenReturn(List.of(participation));
    when(progress.findRange(USER_ID, participation.getJoinedLocalDate(), TODAY))
        .thenReturn(List.of(waterDay(TODAY.minusDays(4)), waterDay(TODAY.minusDays(3)),
            waterDay(TODAY.minusDays(2)), waterDay(TODAY.minusDays(1)), waterDay(TODAY)));
    when(userChallenges.completeIfActive(eq(participation.getId()), eq(5),
        eq(ChallengeStatus.ACTIVE), eq(ChallengeStatus.COMPLETED), any())).thenReturn(0);

    service.refresh(USER_ID, TODAY.minusDays(2));

    verify(progress).findRange(USER_ID, participation.getJoinedLocalDate(), TODAY);
    assertThat(participation.getProgress()).isEqualTo(5);
    verify(events, never()).publishEvent(any());
  }

  @Test
  void completionUpdateResultControlsSideEffects() {
    when(userChallenges.findActiveForDate(USER_ID, TODAY, ChallengeStatus.ACTIVE))
        .thenReturn(List.of(participation));
    when(progress.findRange(USER_ID, participation.getJoinedLocalDate(), TODAY))
        .thenReturn(List.of(waterDay(TODAY.minusDays(4)), waterDay(TODAY.minusDays(3)),
            waterDay(TODAY.minusDays(2)), waterDay(TODAY.minusDays(1)), waterDay(TODAY)));
    when(userChallenges.completeIfActive(eq(participation.getId()), eq(5),
        eq(ChallengeStatus.ACTIVE), eq(ChallengeStatus.COMPLETED), any()))
        .thenReturn(1, 0);

    service.refresh(USER_ID, TODAY);
    service.refresh(USER_ID, TODAY);

    verify(points).award(USER_ID, com.healthcare.kallo.model.game.enums.PointSource.CHALLENGE,
        CHALLENGE_ID, 20);
    verify(events).publishEvent(any(ChallengeCompletedEvent.class));
  }

  @Test
  void endedChallengeRemainsReadableWhenUserParticipated() {
    challenge.setEndDate(TODAY.minusDays(1));
    when(challenges.findById(CHALLENGE_ID)).thenReturn(Optional.of(challenge));
    when(userChallenges.findByUserIdAndChallengeId(USER_ID, CHALLENGE_ID))
        .thenReturn(Optional.of(participation));

    assertThat(service.get(USER_ID, CHALLENGE_ID).joined()).isTrue();
  }

  @Test
  void joiningTwiceAndJoiningEndedChallengeAreRejected() {
    when(challenges.findById(CHALLENGE_ID)).thenReturn(Optional.of(challenge));
    when(userChallenges.findByUserIdAndChallengeId(USER_ID, CHALLENGE_ID))
        .thenReturn(Optional.of(participation));
    assertThatThrownBy(() -> service.join(USER_ID, CHALLENGE_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(com.healthcare.kallo.common.ErrorCode.CHALLENGE_ALREADY_JOINED);

    challenge.setEndDate(TODAY.minusDays(1));
    assertThatThrownBy(() -> service.join(USER_ID, CHALLENGE_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(com.healthcare.kallo.common.ErrorCode.CHALLENGE_NOT_JOINABLE);
  }

  private DailyProgress waterDay(LocalDate date) {
    return new DailyProgress(date, 1, null, null, 2_000, 2_000, 0, null);
  }
}
