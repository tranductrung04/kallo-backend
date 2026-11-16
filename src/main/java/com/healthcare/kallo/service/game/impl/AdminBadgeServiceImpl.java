package com.healthcare.kallo.service.game.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.request.game.CreateBadgeRequest;
import com.healthcare.kallo.dto.request.game.UpdateBadgeRequest;
import com.healthcare.kallo.dto.response.game.AdminBadgeResponse;
import com.healthcare.kallo.model.game.Badge;
import com.healthcare.kallo.repository.game.BadgeRepository;
import com.healthcare.kallo.repository.game.UserBadgeRepository;
import com.healthcare.kallo.service.game.AdminBadgeService;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminBadgeServiceImpl implements AdminBadgeService {
  private final BadgeRepository badges;

  public AdminBadgeServiceImpl(BadgeRepository badges) {
    this.badges = badges;
  }

  @Override
  @Transactional(readOnly = true)
  public List<AdminBadgeResponse> list() {
    return badges.findAll(org.springframework.data.domain.Sort.by("sortOrder").ascending().and(
        org.springframework.data.domain.Sort.by("code").ascending())).stream()
        .map(this::response).toList();
  }

  @Override
  @Transactional
  public AdminBadgeResponse create(CreateBadgeRequest request) {
    validate(request.ruleType(), request.threshold());
    Badge badge = new Badge();
    apply(badge, request);
    try {
      return response(badges.saveAndFlush(badge));
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
  }

  @Override
  @Transactional
  public AdminBadgeResponse update(UUID badgeId, UpdateBadgeRequest request) {
    validate(request.ruleType(), request.threshold());
    Badge badge = badges.findById(badgeId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    apply(badge, request);
    try {
      return response(badges.saveAndFlush(badge));
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
  }

  private void apply(Badge badge, CreateBadgeRequest request) {
    badge.setCode(request.code()); badge.setName(request.name()); badge.setDescription(request.description());
    badge.setIconKey(request.iconKey()); badge.setRuleType(request.ruleType()); badge.setThreshold(request.threshold());
    badge.setPoints(request.points()); badge.setActive(request.active()); badge.setSortOrder(request.sortOrder());
  }

  private void apply(Badge badge, UpdateBadgeRequest request) {
    badge.setCode(request.code()); badge.setName(request.name()); badge.setDescription(request.description());
    badge.setIconKey(request.iconKey()); badge.setRuleType(request.ruleType()); badge.setThreshold(request.threshold());
    badge.setPoints(request.points()); badge.setActive(request.active()); badge.setSortOrder(request.sortOrder());
  }

  private void validate(com.healthcare.kallo.model.game.enums.BadgeRuleType type, Integer threshold) {
    if (type != com.healthcare.kallo.model.game.enums.BadgeRuleType.CHALLENGE
        && type != com.healthcare.kallo.model.game.enums.BadgeRuleType.WEIGHT_GOAL_REACHED
        && (threshold == null || threshold <= 0)) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    if ((type == com.healthcare.kallo.model.game.enums.BadgeRuleType.CHALLENGE
        || type == com.healthcare.kallo.model.game.enums.BadgeRuleType.WEIGHT_GOAL_REACHED)
        && threshold != null && threshold <= 0) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
  }

  private AdminBadgeResponse response(Badge badge) {
    return new AdminBadgeResponse(badge.getId(), badge.getCode(), badge.getName(), badge.getDescription(),
        badge.getIconKey(), badge.getRuleType(), badge.getThreshold(), badge.getPoints(), badge.isActive(),
        badge.getSortOrder());
  }
}
