package com.healthcare.kallo.service.game.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.response.game.BadgeResponse;
import com.healthcare.kallo.event.game.BadgeEarnedEvent;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.game.Badge;
import com.healthcare.kallo.model.game.Streak;
import com.healthcare.kallo.model.game.UserBadge;
import com.healthcare.kallo.model.game.enums.BadgeRuleType;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.game.BadgeRepository;
import com.healthcare.kallo.repository.game.FriendshipRepository;
import com.healthcare.kallo.repository.game.StreakRepository;
import com.healthcare.kallo.repository.game.UserBadgeRepository;
import com.healthcare.kallo.repository.game.UserChallengeRepository;
import com.healthcare.kallo.service.game.BadgeRule;
import com.healthcare.kallo.service.game.GameAuditLogger;
import com.healthcare.kallo.service.game.BadgeRuleContext;
import com.healthcare.kallo.service.game.BadgeService;
import com.healthcare.kallo.service.game.PointService;
import com.healthcare.kallo.service.game.DailyProgressProvider;
import com.healthcare.kallo.service.game.UserDayResolver;
import com.healthcare.kallo.config.GameProperties;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BadgeServiceImpl implements BadgeService {
  private final BadgeRepository badges;
  private final UserBadgeRepository userBadges;
  private final PointService points;
  private final UserRepository users;
  private final StreakRepository streaks;
  private final FriendshipRepository friendships;
  private final UserChallengeRepository userChallenges;
  private final DailyProgressProvider progress;
  private final UserDayResolver days;
  private final GameProperties properties;
  private final ApplicationEventPublisher events;
  private final GameAuditLogger audit;
  private final Map<BadgeRuleType, BadgeRule> rules;

  public BadgeServiceImpl(BadgeRepository badges, UserBadgeRepository userBadges, PointService points,
      UserRepository users, StreakRepository streaks, FriendshipRepository friendships,
      UserChallengeRepository userChallenges, DailyProgressProvider progress, UserDayResolver days,
      GameProperties properties, ApplicationEventPublisher events, GameAuditLogger audit, List<BadgeRule> rules) {
    this.badges = badges;
    this.userBadges = userBadges;
    this.points = points;
    this.users = users;
    this.streaks = streaks;
    this.friendships = friendships;
    this.userChallenges = userChallenges;
    this.progress = progress;
    this.days = days;
    this.properties = properties;
    this.events = events;
    this.audit = audit;
    this.rules = rules.stream().collect(Collectors.toUnmodifiableMap(BadgeRule::type, Function.identity()));
  }

  @Override
  @Transactional(readOnly = true)
  public List<BadgeResponse> list(UUID userId) {
    List<Badge> available = badges.findByActiveTrueOrderBySortOrderAscCodeAsc();
    Map<UUID, UserBadge> earned = userBadges.findByUserId(userId).stream()
        .collect(Collectors.toMap(value -> value.getBadge().getId(), Function.identity()));
    return available.stream().map(badge -> {
      UserBadge userBadge = earned.get(badge.getId());
      return new BadgeResponse(badge.getId(), badge.getCode(), badge.getName(), badge.getDescription(),
          badge.getIconKey(), badge.getPoints(), userBadge != null,
          userBadge == null ? null : userBadge.getEarnedAt());
    }).toList();
  }

  @Override
  @Transactional
  public void evaluate(UUID userId, Collection<BadgeRuleType> triggers) {
    if (triggers == null || triggers.isEmpty()) return;
    var types = triggers.stream().distinct().toList();
    BadgeRuleContext context = context(userId, types.contains(BadgeRuleType.FIRST_LOG));
    for (Badge badge : badges.findActiveByRuleTypeIn(types, userId)) {
      BadgeRule rule = rules.get(badge.getRuleType());
      if (rule != null && rule.isSatisfied(context, badge)) grantNew(userId, badge);
    }
  }

  @Override
  @Transactional
  public boolean grant(UUID userId, UUID badgeId) {
    Badge badge = badges.findById(badgeId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    if (!badge.isActive()) return false;
    return grantNew(userId, badge);
  }

  private boolean grantNew(UUID userId, Badge badge) {
    if (userBadges.insertIfAbsent(userId, badge.getId()) != 1) return false;
    points.award(userId, com.healthcare.kallo.model.game.enums.PointSource.BADGE,
        badge.getId(), badge.getPoints());
    events.publishEvent(new BadgeEarnedEvent(userId, badge.getId(), badge.getPoints()));
    audit.badge("badge_earned", userId, badge.getId());
    return true;
  }

  private BadgeRuleContext context(UUID userId, boolean firstLog) {
    Streak streak = streaks.findById(userId).orElse(null);
    boolean hasLog = !firstLog || progress.findRange(userId, startDate(userId), today(userId)).stream()
        .anyMatch(value -> value != null && value.mealsLogged() > 0);
    return new BadgeRuleContext(userId, streak == null ? 0 : streak.getLongestStreak(), firstLog && hasLog,
        friendships.countAcceptedByUserId(userId),
        userChallenges.countByUserIdAndStatus(userId, com.healthcare.kallo.model.game.enums.ChallengeStatus.COMPLETED),
        false);
  }

  private User user(UUID userId) {
    return users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
  }

  private LocalDate startDate(UUID userId) {
    return today(userId).minusDays(properties.streak().windowDays() - 1L);
  }

  private LocalDate today(UUID userId) {
    return days.today(user(userId));
  }

}
