package com.healthcare.kallo.repository.act;

import com.healthcare.kallo.model.act.WeightLog;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface WeightLogRepository extends JpaRepository<WeightLog, UUID> {
  Optional<WeightLog> findByUserIdAndClientUuid(UUID userId, UUID clientUuid);

  Optional<WeightLog> findByIdAndUserId(UUID id, UUID userId);

  Optional<WeightLog> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

  @Query("""
      select w from WeightLog w
      where w.user.id = :userId and w.updatedAt > :since
      order by w.updatedAt asc, w.id asc
      """)
  List<WeightLog> findChangedSince(@Param("userId") UUID userId,
      @Param("since") Instant since, Pageable pageable);

  @Modifying
  @Transactional
  @Query("delete from WeightLog w where w.deletedAt is not null and w.deletedAt < :cutoff")
  int deleteTombstonesBefore(@Param("cutoff") Instant cutoff);

  @Query("""
      select w from WeightLog w
      where w.user.id = :userId
        and w.loggedAt >= :from
        and w.loggedAt < :to
        and w.deletedAt is null
      order by w.loggedAt asc
      """)
  List<WeightLog> findByUserIdAndLoggedAtInRange(
      @Param("userId") UUID userId,
      @Param("from") Instant from,
      @Param("to") Instant to);

  Optional<WeightLog> findTopByUserIdAndDeletedAtIsNullAndLoggedAtLessThanEqualOrderByLoggedAtDesc(
      UUID userId, Instant instant);

  default Optional<WeightLog> findLatestAtOrBefore(UUID userId, Instant instant) {
    return findTopByUserIdAndDeletedAtIsNullAndLoggedAtLessThanEqualOrderByLoggedAtDesc(
        userId, instant);
  }

  @Modifying
  @Transactional
  @Query(
      value =
          """
          INSERT INTO weight_logs (user_id, client_uuid, weight_kg, logged_at, note, created_at, updated_at)
          VALUES (:userId, :clientUuid, :weightKg, :loggedAt, :note, NOW(), NOW())
          ON CONFLICT (user_id, client_uuid) DO NOTHING
          """,
      nativeQuery = true)
  int insertIfAbsent(
      @Param("userId") UUID userId,
      @Param("clientUuid") UUID clientUuid,
      @Param("weightKg") BigDecimal weightKg,
      @Param("loggedAt") Instant loggedAt,
      @Param("note") String note);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query(
      value =
          """
          UPDATE weight_logs
          SET deleted_at = :deletedAt, updated_at = :deletedAt
          WHERE id = :id AND user_id = :userId AND deleted_at IS NULL
          """,
      nativeQuery = true)
  int softDeleteByIdAndUserId(
      @Param("id") UUID id,
      @Param("userId") UUID userId,
      @Param("deletedAt") Instant deletedAt);
}
