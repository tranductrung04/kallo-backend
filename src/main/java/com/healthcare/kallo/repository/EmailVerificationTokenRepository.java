package com.healthcare.kallo.repository;

import com.healthcare.kallo.model.EmailVerificationToken;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface EmailVerificationTokenRepository
    extends JpaRepository<EmailVerificationToken, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<EmailVerificationToken> findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(UUID userId);

  @Modifying
  @Query("update EmailVerificationToken t set t.usedAt = :now where t.user.id = :userId and t.usedAt is null")
  int invalidateActive(@Param("userId") UUID userId, @Param("now") Instant now);

  long countByUserIdAndCreatedAtAfter(UUID userId, Instant after);
}
