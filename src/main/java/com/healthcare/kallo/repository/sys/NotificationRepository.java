package com.healthcare.kallo.repository.sys;

import com.healthcare.kallo.model.sys.Notification;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
  @Query("""
      select n from Notification n
      where n.userId = :userId and n.deletedAt is null
        and (:unreadOnly = false or n.read = false)
      order by n.createdAt desc, n.id desc
      """)
  List<Notification> findFirstPage(
      @Param("userId") UUID userId,
      @Param("unreadOnly") boolean unreadOnly,
      org.springframework.data.domain.Pageable pageable);

  @Query("""
      select n from Notification n
      where n.userId = :userId and n.deletedAt is null
        and (:unreadOnly = false or n.read = false)
        and (n.createdAt < :beforeCreated
             or (n.createdAt = :beforeCreated and n.id < :beforeId))
      order by n.createdAt desc, n.id desc
      """)
  List<Notification> findPageAfter(
      @Param("userId") UUID userId,
      @Param("unreadOnly") boolean unreadOnly,
      @Param("beforeCreated") Instant beforeCreated,
      @Param("beforeId") UUID beforeId,
      org.springframework.data.domain.Pageable pageable);

  long countByUserIdAndReadFalseAndDeletedAtIsNull(UUID userId);

  @Modifying
  @Query("""
      update Notification n set n.read = true, n.readAt = coalesce(n.readAt, :readAt)
      where n.userId = :userId and n.deletedAt is null and n.read = false
      """)
  int markAllRead(@Param("userId") UUID userId, @Param("readAt") Instant readAt);

  @Modifying
  @Query("""
      update Notification n set n.read = true, n.readAt = :readAt
      where n.id = :id and n.userId = :userId and n.deletedAt is null
      """)
  int markRead(@Param("id") UUID id, @Param("userId") UUID userId, @Param("readAt") Instant readAt);

  @Modifying
  @Query("""
      update Notification n set n.deletedAt = :deletedAt
      where n.id = :id and n.userId = :userId and n.deletedAt is null
      """)
  int softDelete(@Param("id") UUID id, @Param("userId") UUID userId, @Param("deletedAt") Instant deletedAt);

  @Modifying
  @Query("update Notification n set n.pushedAt = :pushedAt where n.id = :id")
  int markPushed(@Param("id") UUID id, @Param("pushedAt") Instant pushedAt);
}
