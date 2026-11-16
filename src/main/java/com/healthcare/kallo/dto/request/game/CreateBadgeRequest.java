package com.healthcare.kallo.dto.request.game;

import com.healthcare.kallo.model.game.enums.BadgeRuleType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBadgeRequest(
    @NotBlank @Size(max = 50) String code,
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Size(max = 500) String description,
    @Size(max = 200) String iconKey,
    @NotNull BadgeRuleType ruleType,
    Integer threshold,
    @NotNull @Min(0) Integer points,
    @NotNull Boolean active,
    @NotNull @Min(0) Integer sortOrder) {}
