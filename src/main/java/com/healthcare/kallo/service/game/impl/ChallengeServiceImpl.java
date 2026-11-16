package com.healthcare.kallo.service.game.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.response.game.ChallengeResponse;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.game.Challenge;
import com.healthcare.kallo.model.game.UserChallenge;
import com.healthcare.kallo.model.game.enums.BadgeRuleType;
import com.healthcare.kallo.model.game.enums.ChallengeStatus;
import com.healthcare.kallo.model.game.enums.PointSource;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.game.ChallengeRepository;
import com.healthcare.kallo.repository.game.UserChallengeRepository;
import com.healthcare.kallo.service.game.BadgeService;
import com.healthcare.kallo.service.game.ChallengeMetricEvaluator;
import com.healthcare.kallo.service.game.ChallengeService;
import com.healthcare.kallo.service.game.DailyGoalPolicy;
import com.healthcare.kallo.service.game.DailyProgress;
import com.healthcare.kallo.service.game.DailyProgressProvider;
import com.healthcare.kallo.service.game.PointService;
import com.healthcare.kallo.service.game.UserDayResolver;
import com.healthcare.kallo.event.game.ChallengeCompletedEvent;
import org.springframework.context.ApplicationEventPublisher;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChallengeServiceImpl implements ChallengeService {
  private final ChallengeRepository challenges;
  private final UserChallengeRepository userChallenges;
  private final UserRepository users;
  private final DailyProgressProvider progress;
  private final DailyGoalPolicy dailyGoalPolicy;
  private final UserDayResolver days;
  private final PointService points;
  private final BadgeService badges;
  private final ApplicationEventPublisher events;
  private final java.time.Clock clock;

  public ChallengeServiceImpl(ChallengeRepository challenges, UserChallengeRepository userChallenges,
      UserRepository users, DailyProgressProvider progress, DailyGoalPolicy dailyGoalPolicy,
      UserDayResolver days, PointService points, BadgeService badges, ApplicationEventPublisher events,
      java.time.Clock clock) {
    this.challenges = challenges;
    this.userChallenges = userChallenges;
    this.users = users;
    this.progress = progress;
    this.dailyGoalPolicy = dailyGoalPolicy;
    this.days = days;
    this.points = points;
    this.badges = badges;
    this.events = events;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public List<ChallengeResponse> list(UUID userId, String status) {
    User user = user(userId);
    LocalDate today = days.today(user);
    String filter = status == null || status.isBlank() ? "available" : status.toLowerCase(Locale.ROOT);
    if (!filter.equals("available") && !filter.equals("joined") && !filter.equals("completed")) {
      throw new BusinessException(ErrorCode.INVALID_PARAMETER);
    }
    List<Challenge> available = challenges.findActive();
    var joined = userChallenges.findByUserIdAndStatus(userId, ChallengeStatus.ACTIVE);
    var completed = userChallenges.findByUserIdAndStatus(userId, ChallengeStatus.COMPLETED);
    Map<UUID, UserChallenge> participations = new HashMap<>();
    joined.forEach(value -> participations.put(value.getChallenge().getId(), value));
    completed.forEach(value -> participations.put(value.getChallenge().getId(), value));
    var result = available.stream()
        .filter(challenge -> filterMatches(filter, challenge, today, participations))
        .map(challenge -> response(challenge, participations.get(challenge.getId()), today))
        .toList();
    return result;
  }

  private boolean filterMatches(String filter, Challenge challenge, LocalDate today,
      Map<UUID, UserChallenge> participations) {
    UserChallenge participation = participations.get(challenge.getId());
    return switch (filter) {
      case "joined" -> participation != null && participation.getStatus() == ChallengeStatus.ACTIVE;
      case "completed" -> participation != null && participation.getStatus() == ChallengeStatus.COMPLETED;
      default -> challenge.getStartDate().compareTo(today) <= 0 && challenge.getEndDate().compareTo(today) >= 0
          && participation == null;
    };
  }

  @Override
  @Transactional(readOnly = true)
  public ChallengeResponse get(UUID userId, UUID challengeId) {
    User user = user(userId);
    Challenge challenge = challenges.findById(challengeId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    LocalDate today = days.today(user);
    UserChallenge participation = userChallenges.findByUserIdAndChallengeId(userId, challengeId).orElse(null);
    if (!challenge.isActive() || (challenge.getEndDate().isBefore(today) && participation == null)) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    return response(challenge, participation, today);
  }

  @Override
  @Transactional
  public ChallengeResponse join(UUID userId, UUID challengeId) {
    User user = user(userId);
    Challenge challenge = challenges.findById(challengeId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    LocalDate today = days.today(user);
    if (!challenge.isActive() || challenge.getStartDate().isAfter(today) || challenge.getEndDate().isBefore(today)) {
      throw new BusinessException(ErrorCode.CHALLENGE_NOT_JOINABLE);
    }
    if (userChallenges.findByUserIdAndChallengeId(userId, challengeId).isPresent()) {
      throw new BusinessException(ErrorCode.CHALLENGE_ALREADY_JOINED);
    }
    UserChallenge participation = new UserChallenge();
    participation.setUser(user);
    participation.setChallenge(challenge);
    participation.setProgress(0);
    participation.setStatus(ChallengeStatus.ACTIVE);
    participation.setJoinedLocalDate(today);
    try {
      participation = userChallenges.saveAndFlush(participation);
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(ErrorCode.CHALLENGE_ALREADY_JOINED);
    }
    refresh(userId, today);
    return response(challenge, userChallenges.findById(participation.getId()).orElse(participation), today);
  }

  @Override
  @Transactional
  public void refresh(UUID userId, LocalDate localDate) {
    if (localDate == null) return;
    User user = user(userId);
    LocalDate today = days.today(user);
    for (UserChallenge current : userChallenges.findActiveForDate(userId, localDate, ChallengeStatus.ACTIVE)) {
      Challenge challenge = current.getChallenge();
      LocalDate from = max(challenge.getStartDate(), current.getJoinedLocalDate());
      LocalDate to = min(challenge.getEndDate(), today);
      if (from.isAfter(to)) continue;
      List<DailyProgress> daily = progress.findRange(userId, from, to);
      int value = ChallengeMetricEvaluator.evaluate(challenge.getMetric(), daily, dailyGoalPolicy);
      current.setProgress(Math.max(0, value));
      if (value < challenge.getTargetValue()) continue;
      if (userChallenges.completeIfActive(current.getId(), Math.max(0, value), ChallengeStatus.ACTIVE,
          ChallengeStatus.COMPLETED, clock.instant()) != 1) continue;
      points.award(userId, PointSource.CHALLENGE, challenge.getId(), challenge.getPointsReward());
      UUID badgeId = challenge.getBadge() == null ? null : challenge.getBadge().getId();
      if (badgeId != null) badges.grant(userId, badgeId);
      badges.evaluate(userId, java.util.Set.of(BadgeRuleType.CHALLENGES_COMPLETED));
      events.publishEvent(new ChallengeCompletedEvent(userId, challenge.getId(),
          challenge.getPointsReward(), badgeId));
    }
  }

  private UserChallenge findParticipation(Challenge challenge, List<UserChallenge> active,
      List<UserChallenge> completed) {
    return java.util.stream.Stream.concat(active.stream(), completed.stream())
        .filter(value -> value.getChallenge().getId().equals(challenge.getId())).findFirst().orElse(null);
  }

  private ChallengeResponse response(Challenge challenge, UserChallenge participation, LocalDate today) {
    long daysLeft = challenge.getEndDate().isBefore(today) ? 0
        : java.time.temporal.ChronoUnit.DAYS.between(today, challenge.getEndDate()) + 1;
    return new ChallengeResponse(challenge.getId(), challenge.getTitle(), challenge.getDescription(),
        challenge.getMetric(), challenge.getTargetValue(), challenge.getStartDate(), challenge.getEndDate(),
        challenge.getPointsReward(), challenge.getBadge() == null ? null : challenge.getBadge().getId(),
        participation != null, participation == null ? 0 : participation.getProgress(),
        participation == null ? null : participation.getStatus(), daysLeft);
  }

  private User user(UUID userId) {
    return users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
  }

  private static LocalDate max(LocalDate first, LocalDate second) {
    return first.isAfter(second) ? first : second;
  }

  private static LocalDate min(LocalDate first, LocalDate second) {
    return first.isBefore(second) ? first : second;
  }
}
