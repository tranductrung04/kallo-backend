package com.healthcare.kallo.dto.response.game;

import com.healthcare.kallo.model.game.enums.BadgeRuleType;
import java.util.UUID;

public record AdminBadgeResponse(
    UUID id,
    String code,
    String name,
    String description,
    String iconKey,
    BadgeRuleType ruleType,
    Integer threshold,
    int points,
    boolean active,
    int sortOrder) {}
