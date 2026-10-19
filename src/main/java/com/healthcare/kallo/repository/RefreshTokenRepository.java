package com.healthcare.kallo.repository;

import com.healthcare.kallo.model.RefreshToken;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
  Optional<RefreshToken> findByTokenHash(String hash);

  @Modifying
  @Query(
      "update RefreshToken r set r.revokedAt=:now where r.user.id=:userId and r.revokedAt is null")
  int revokeByUser(@Param("userId") UUID id, @Param("now") Instant now);

  @Modifying
  @Query("delete from RefreshToken r where r.expiresAt < :now")
  int deleteExpired(@Param("now") Instant now);
}
