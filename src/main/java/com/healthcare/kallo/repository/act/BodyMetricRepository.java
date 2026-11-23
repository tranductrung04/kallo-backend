package com.healthcare.kallo.repository.act;

import com.healthcare.kallo.model.act.BodyMetric;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface BodyMetricRepository extends JpaRepository<BodyMetric, UUID> {
  Optional<BodyMetric> findByUserIdAndClientUuid(UUID userId, UUID clientUuid);
  Optional<BodyMetric> findByIdAndUserId(UUID id, UUID userId);
  Optional<BodyMetric> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
  List<BodyMetric> findByUserIdAndLoggedAtBetweenAndDeletedAtIsNullOrderByLoggedAtAsc(UUID userId, Instant from, Instant to);

  @Query("select b from BodyMetric b where b.user.id = :userId and b.updatedAt > :since order by b.updatedAt asc, b.id asc")
  List<BodyMetric> findChangedSince(@Param("userId") UUID userId, @Param("since") Instant since, Pageable pageable);

  @Modifying @Transactional
  @Query("delete from BodyMetric b where b.deletedAt is not null and b.deletedAt < :cutoff")
  int deleteTombstonesBefore(@Param("cutoff") Instant cutoff);

  @Modifying @Transactional
  @Query(value = "UPDATE body_metrics SET deleted_at = :deletedAt, updated_at = :deletedAt WHERE id = :id AND user_id = :userId AND deleted_at IS NULL", nativeQuery = true)
  int softDeleteByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId, @Param("deletedAt") Instant deletedAt);

  @Modifying @Transactional
  @Query(value = "INSERT INTO body_metrics (user_id, client_uuid, waist_cm, hip_cm, logged_at, note, created_at, updated_at) VALUES (:userId, :clientUuid, :waistCm, :hipCm, :loggedAt, :note, NOW(), NOW()) ON CONFLICT (user_id, client_uuid) DO NOTHING", nativeQuery = true)
  int insertIfAbsent(@Param("userId") UUID userId, @Param("clientUuid") UUID clientUuid,
      @Param("waistCm") java.math.BigDecimal waistCm, @Param("hipCm") java.math.BigDecimal hipCm,
      @Param("loggedAt") Instant loggedAt, @Param("note") String note);
}
