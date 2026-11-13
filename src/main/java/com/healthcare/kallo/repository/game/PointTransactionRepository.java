package com.healthcare.kallo.repository.game;

import com.healthcare.kallo.model.game.PointTransaction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, UUID> {
  interface PointSum {
    UUID getUserId();
    Long getPoints();
  }

  @Modifying
  @Transactional
  @Query(value = """
      INSERT INTO point_transactions (user_id, points, source_type, source_id, occurred_at)
      VALUES (:userId, :points, :sourceType, :sourceId, now())
      ON CONFLICT (user_id, source_type, source_id) DO NOTHING
      """, nativeQuery = true)
  int insertIfAbsent(@Param("userId") UUID userId, @Param("points") int points,
      @Param("sourceType") String sourceType, @Param("sourceId") UUID sourceId);

  @Query("""
      select p.user.id as userId, sum(p.points) as points
      from PointTransaction p
      where p.user.id in :userIds and p.occurredAt >= :from and p.occurredAt < :to
      group by p.user.id
      """)
  List<PointSum> sumPoints(@Param("userIds") List<UUID> userIds,
      @Param("from") Instant from, @Param("to") Instant to);
}
