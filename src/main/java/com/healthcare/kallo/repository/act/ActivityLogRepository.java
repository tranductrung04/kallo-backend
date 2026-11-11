package com.healthcare.kallo.repository.act;

import com.healthcare.kallo.model.act.ActivityLog;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {
  Optional<ActivityLog> findByUserIdAndClientUuid(UUID userId, UUID clientUuid);
  Optional<ActivityLog> findByIdAndUserId(UUID id, UUID userId);
  Optional<ActivityLog> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

  @Query("""
      select a from ActivityLog a join fetch a.activityType
      where a.user.id = :userId and a.deletedAt is null
      order by a.startedAt
      """)
  List<ActivityLog> findActiveForDeduplication(@Param("userId") UUID userId);

  @Query("""
      select a from ActivityLog a join fetch a.activityType
      where a.user.id = :userId and a.startedAt < :to and a.endedAt > :from
        and a.deletedAt is null and a.supersededBy is null
      order by a.startedAt
      """)
  List<ActivityLog> findActiveOverlapping(@Param("userId") UUID userId,
      @Param("from") Instant from, @Param("to") Instant to);

  @Query("""
      select a from ActivityLog a where a.user.id = :userId
      and a.startedAt >= :from and a.startedAt < :to
      and a.deletedAt is null
      and (:includeSuperseded = true or a.supersededBy is null)
      order by a.startedAt desc, a.id desc
      """)
  Page<ActivityLog> findInRange(@Param("userId") UUID userId, @Param("from") Instant from,
      @Param("to") Instant to, @Param("includeSuperseded") boolean includeSuperseded, Pageable pageable);

  @Modifying
  @Transactional
  @Query(value = """
      INSERT INTO activity_logs
      (user_id, client_uuid, activity_type, intensity, met, weight_kg_used, duration_min,
       distance_km, calories_burned, source, external_id, started_at, ended_at, note, route_polyline, created_at, updated_at)
      VALUES (:userId, :clientUuid, :activityType, :intensity, :met, :weight, :duration,
              :distance, :calories, :source, :externalId, :startedAt, :endedAt, :note, :routePolyline, NOW(), NOW())
      ON CONFLICT (user_id, client_uuid) DO NOTHING
      """, nativeQuery = true)
  int insertIfAbsent(@Param("userId") UUID userId, @Param("clientUuid") UUID clientUuid,
      @Param("activityType") String activityType, @Param("intensity") String intensity,
      @Param("met") java.math.BigDecimal met, @Param("weight") java.math.BigDecimal weight,
      @Param("duration") int duration, @Param("distance") java.math.BigDecimal distance,
      @Param("calories") java.math.BigDecimal calories, @Param("source") String source,
      @Param("externalId") String externalId, @Param("startedAt") Instant startedAt,
      @Param("endedAt") Instant endedAt, @Param("note") String note,
      @Param("routePolyline") String routePolyline);

  @Modifying
  @Transactional
  @Query(value = """
      UPDATE activity_logs SET deleted_at = :deletedAt, updated_at = :deletedAt
      WHERE id = :id AND user_id = :userId AND deleted_at IS NULL
      """, nativeQuery = true)
  int softDeleteByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId,
      @Param("deletedAt") Instant deletedAt);

}
