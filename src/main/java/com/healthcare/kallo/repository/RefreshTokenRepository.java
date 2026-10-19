package com.healthcare.kallo.repository;

import com.healthcare.kallo.model.RefreshToken;
import com.healthcare.kallo.model.User;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<RefreshToken> findByTokenHash(String hash);

  Optional<RefreshToken> findFirstByUserOrderByCreatedAtDesc(User user);

  Optional<RefreshToken> findFirstByUserAndRevokedAtIsNotNullOrderByCreatedAtDesc(User user);

  @Modifying
  @Query(
      "update RefreshToken r set r.revokedAt=:now where r.familyId=:familyId and r.revokedAt is null")
  int revokeByFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

  @Modifying
  @Query(
      "update RefreshToken r set r.revokedAt=:now where r.user.id=:userId and r.revokedAt is null")
  int revokeByUser(@Param("userId") UUID id, @Param("now") Instant now);

  @Modifying
  @Query("delete from RefreshToken r where r.expiresAt < :now")
  int deleteExpired(@Param("now") Instant now);
}
