package com.healthcare.kallo.service.game;

import com.healthcare.kallo.dto.response.game.BadgeResponse;
import com.healthcare.kallo.model.game.enums.BadgeRuleType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BadgeService {
  List<BadgeResponse> list(UUID userId);

  void evaluate(UUID userId, Collection<BadgeRuleType> triggers);

  boolean grant(UUID userId, UUID badgeId);
}
