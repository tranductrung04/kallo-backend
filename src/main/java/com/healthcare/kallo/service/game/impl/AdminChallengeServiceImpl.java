package com.healthcare.kallo.service.game.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.request.game.CreateChallengeRequest;
import com.healthcare.kallo.dto.request.game.UpdateChallengeRequest;
import com.healthcare.kallo.dto.response.game.AdminChallengeResponse;
import com.healthcare.kallo.model.game.Badge;
import com.healthcare.kallo.model.game.Challenge;
import com.healthcare.kallo.repository.game.BadgeRepository;
import com.healthcare.kallo.repository.game.ChallengeRepository;
import com.healthcare.kallo.repository.game.UserChallengeRepository;
import com.healthcare.kallo.service.game.AdminChallengeService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminChallengeServiceImpl implements AdminChallengeService {
  private final ChallengeRepository challenges;
  private final BadgeRepository badges;
  private final UserChallengeRepository userChallenges;

  public AdminChallengeServiceImpl(ChallengeRepository challenges, BadgeRepository badges,
      UserChallengeRepository userChallenges) {
    this.challenges = challenges;
    this.badges = badges;
    this.userChallenges = userChallenges;
  }

  @Override
  @Transactional(readOnly = true)
  public List<AdminChallengeResponse> list() {
    return challenges.findAll(org.springframework.data.domain.Sort.by("endDate").ascending()).stream()
        .map(this::response).toList();
  }

  @Override
  @Transactional
  public AdminChallengeResponse create(CreateChallengeRequest request) {
    validateDates(request.startDate(), request.endDate());
    Challenge challenge = new Challenge();
    apply(challenge, request);
    try {
      return response(challenges.saveAndFlush(challenge));
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
  }

  @Override
  @Transactional
  public AdminChallengeResponse update(UUID challengeId, UpdateChallengeRequest request) {
    validateDates(request.startDate(), request.endDate());
    Challenge challenge = challenges.findById(challengeId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    if (userChallenges.existsByChallengeId(challengeId)
        && (challenge.getMetric() != request.metric() || challenge.getTargetValue() != request.targetValue())) {
      throw new BusinessException(ErrorCode.CHALLENGE_LOCKED);
    }
    apply(challenge, request);
    try {
      return response(challenges.saveAndFlush(challenge));
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
  }

  private void apply(Challenge challenge, CreateChallengeRequest request) {
    challenge.setTitle(request.title()); challenge.setDescription(request.description()); challenge.setMetric(request.metric());
    challenge.setTargetValue(request.targetValue()); challenge.setStartDate(request.startDate()); challenge.setEndDate(request.endDate());
    challenge.setPointsReward(request.pointsReward()); challenge.setBadge(badge(request.badgeId())); challenge.setActive(request.active());
  }

  private void apply(Challenge challenge, UpdateChallengeRequest request) {
    challenge.setTitle(request.title()); challenge.setDescription(request.description()); challenge.setMetric(request.metric());
    challenge.setTargetValue(request.targetValue()); challenge.setStartDate(request.startDate()); challenge.setEndDate(request.endDate());
    challenge.setPointsReward(request.pointsReward()); challenge.setBadge(badge(request.badgeId())); challenge.setActive(request.active());
  }

  private Badge badge(UUID id) {
    if (id == null) return null;
    Badge badge = badges.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    if (!badge.isActive() || badge.getRuleType() != com.healthcare.kallo.model.game.enums.BadgeRuleType.CHALLENGE) {
      throw new BusinessException(ErrorCode.INVALID_PARAMETER);
    }
    return badge;
  }

  private void validateDates(LocalDate start, LocalDate end) {
    if (end.isBefore(start)) throw new BusinessException(ErrorCode.INVALID_PARAMETER);
  }

  private AdminChallengeResponse response(Challenge challenge) {
    return new AdminChallengeResponse(challenge.getId(), challenge.getTitle(), challenge.getDescription(),
        challenge.getMetric(), challenge.getTargetValue(), challenge.getStartDate(), challenge.getEndDate(),
        challenge.getPointsReward(), challenge.getBadge() == null ? null : challenge.getBadge().getId(),
        challenge.isActive());
  }
}
