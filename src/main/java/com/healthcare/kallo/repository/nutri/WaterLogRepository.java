package com.healthcare.kallo.repository.nutri;

import com.healthcare.kallo.model.nutri.WaterLog;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WaterLogRepository extends JpaRepository<WaterLog, UUID> {

  Optional<WaterLog> findByUserIdAndClientUuid(UUID userId, UUID clientUuid);

  Optional<WaterLog> findByIdAndUserId(UUID id, UUID userId);

  Optional<WaterLog> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

  List<WaterLog> findByUserIdAndLoggedDateAndDeletedAtIsNullOrderByLoggedAtAsc(
      UUID userId, LocalDate loggedDate);

  @Query("""
      SELECT COALESCE(SUM(w.amountMl), 0)
      FROM WaterLog w
      WHERE w.userId = :userId
        AND w.loggedDate = :loggedDate
        AND w.deletedAt IS NULL
      """)
  int sumAmountMlByUserIdAndLoggedDate(
      @Param("userId") UUID userId,
      @Param("loggedDate") LocalDate loggedDate);

  @Query("""
      SELECT MAX(w.loggedAt)
      FROM WaterLog w
      WHERE w.userId = :userId
        AND w.deletedAt IS NULL
      """)
  Optional<Instant> findLastLoggedAtByUserId(@Param("userId") UUID userId);

  @Modifying
  @Query(value = """
      INSERT INTO water_logs (
          id, user_id, amount_ml, logged_date, logged_at, note,
          client_uuid, created_at, updated_at
      ) VALUES (
          :id, :userId, :amountMl, :loggedDate, :loggedAt, :note,
          :clientUuid, :createdAt, :updatedAt
      )
      ON CONFLICT (user_id, client_uuid) DO NOTHING
      """, nativeQuery = true)
  int insertIfAbsent(
      @Param("id") UUID id,
      @Param("userId") UUID userId,
      @Param("amountMl") Integer amountMl,
      @Param("loggedDate") LocalDate loggedDate,
      @Param("loggedAt") Instant loggedAt,
      @Param("note") String note,
      @Param("clientUuid") UUID clientUuid,
      @Param("createdAt") Instant createdAt,
      @Param("updatedAt") Instant updatedAt);

  @Modifying
  @Query(value = """
      INSERT INTO water_logs (
          id, user_id, logged_at,
          client_uuid, created_at, updated_at, deleted_at
      ) VALUES (
          :id, :userId, :deletedAt,
          :clientUuid, :deletedAt, :updatedAt, :deletedAt
      )
      ON CONFLICT (user_id, client_uuid) DO NOTHING
      """, nativeQuery = true)
  int insertTombstoneIfAbsent(
      @Param("id") UUID id,
      @Param("userId") UUID userId,
      @Param("clientUuid") UUID clientUuid,
      @Param("updatedAt") Instant updatedAt,
      @Param("deletedAt") Instant deletedAt);

  @Query("""
      SELECT w FROM WaterLog w
      WHERE w.userId = :userId
        AND w.syncSeq > :sinceSeq
        AND w.serverUpdatedAt <= :settledBefore
      ORDER BY w.syncSeq ASC
      """)
  List<WaterLog> findSyncChanges(
      @Param("userId") UUID userId,
      @Param("sinceSeq") long sinceSeq,
      @Param("settledBefore") Instant settledBefore,
      Pageable pageable);

  @Modifying
  @Query("DELETE FROM WaterLog w WHERE w.deletedAt IS NOT NULL AND w.deletedAt < :cutoff")
  int deleteTombstonesBefore(@Param("cutoff") Instant cutoff);
}
