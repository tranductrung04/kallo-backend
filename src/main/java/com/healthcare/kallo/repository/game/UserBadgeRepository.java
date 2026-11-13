package com.healthcare.kallo.repository.game;

import com.healthcare.kallo.model.game.UserBadge;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserBadgeRepository extends JpaRepository<UserBadge, UUID> {
  @Query("""
      select ub from UserBadge ub
      join fetch ub.badge
      where ub.user.id = :userId
      order by ub.earnedAt desc, ub.id desc
      """)
  List<UserBadge> findByUserId(@Param("userId") UUID userId);

  boolean existsByUserIdAndBadgeId(UUID userId, UUID badgeId);

  @Modifying
  @Transactional
  @Query(value = """
      INSERT INTO user_badges (user_id, badge_id, earned_at)
      VALUES (:userId, :badgeId, now())
      ON CONFLICT (user_id, badge_id) DO NOTHING
      """, nativeQuery = true)
  int insertIfAbsent(@Param("userId") UUID userId, @Param("badgeId") UUID badgeId);
}
