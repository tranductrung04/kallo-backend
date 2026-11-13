package com.healthcare.kallo.repository.game;

import com.healthcare.kallo.model.game.UserChallenge;
import com.healthcare.kallo.model.game.enums.ChallengeStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserChallengeRepository extends JpaRepository<UserChallenge, UUID> {
  Optional<UserChallenge> findByUserIdAndChallengeId(UUID userId, UUID challengeId);
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

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select uc from UserChallenge uc where uc.id = :id")
  Optional<UserChallenge> findByIdForUpdate(@Param("id") UUID id);
}
