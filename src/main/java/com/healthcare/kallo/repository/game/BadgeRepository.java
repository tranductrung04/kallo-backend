package com.healthcare.kallo.repository.game;

import com.healthcare.kallo.model.game.Badge;
import com.healthcare.kallo.model.game.enums.BadgeRuleType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BadgeRepository extends JpaRepository<Badge, UUID> {
  List<Badge> findByActiveTrueOrderBySortOrderAscCodeAsc();

  Optional<Badge> findByCode(String code);

  @Query("""
      select b from Badge b
      where b.active = true and b.ruleType in :ruleTypes
        and not exists (
          select ub.id from UserBadge ub
          where ub.user.id = :userId and ub.badge.id = b.id
        )
      """)
  List<Badge> findActiveByRuleTypeIn(@Param("ruleTypes") List<BadgeRuleType> ruleTypes,
      @Param("userId") UUID userId);

}
