package com.healthcare.kallo.repository.game;

import com.healthcare.kallo.model.game.UserChallenge;
import com.healthcare.kallo.model.game.enums.ChallengeStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserChallengeRepository extends JpaRepository<UserChallenge, UUID> {
  Optional<UserChallenge> findByUserIdAndChallengeId(UUID userId, UUID challengeId);

  long countByUserIdAndStatus(UUID userId, ChallengeStatus status);

  @Query("""
      select uc from UserChallenge uc
      join fetch uc.challenge c
      left join fetch c.badge
      where uc.user.id = :userId and uc.status = :status
      """)
  List<UserChallenge> findByUserIdAndStatus(@Param("userId") UUID userId,
      @Param("status") ChallengeStatus status);

  @Query("""
      select uc from UserChallenge uc
      join fetch uc.challenge c
      left join fetch c.badge
      where uc.user.id = :userId and uc.status = :status
        and uc.joinedLocalDate <= :date
        and uc.challenge.startDate <= :date and uc.challenge.endDate >= :date
      """)
  List<UserChallenge> findActiveForDate(@Param("userId") UUID userId,
      @Param("date") LocalDate date, @Param("status") ChallengeStatus status);

  @Query("""
      select uc.id from UserChallenge uc
      where uc.status = :status and uc.challenge.endDate < :date
      order by uc.id
      """)
  List<UUID> findExpiredIds(@Param("status") ChallengeStatus status,
      @Param("date") LocalDate date, Pageable pageable);

  @Query("""
      select uc.id from UserChallenge uc
      where uc.status = :status and uc.challenge.endDate < :date and uc.id > :after
      order by uc.id
      """)
  List<UUID> findExpiredIdsAfter(@Param("status") ChallengeStatus status,
      @Param("date") LocalDate date, @Param("after") UUID after, Pageable pageable);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
      update UserChallenge uc
      set uc.progress = :progress, uc.status = :completed, uc.completedAt = :completedAt
      where uc.id = :id and uc.status = :active
      """)
  int completeIfActive(@Param("id") UUID id, @Param("progress") int progress,
      @Param("active") ChallengeStatus active, @Param("completed") ChallengeStatus completed,
      @Param("completedAt") Instant completedAt);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
      update UserChallenge uc
      set uc.status = :expired
      where uc.id = :id and uc.status = :active
      """)
  int expireIfActive(@Param("id") UUID id, @Param("active") ChallengeStatus active,
      @Param("expired") ChallengeStatus expired);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select uc from UserChallenge uc join fetch uc.challenge c left join fetch c.badge where uc.id = :id")
  Optional<UserChallenge> findByIdForUpdate(@Param("id") UUID id);
}
