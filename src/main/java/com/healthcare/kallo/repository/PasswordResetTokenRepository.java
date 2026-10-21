package com.healthcare.kallo.repository;

import com.healthcare.kallo.model.PasswordResetToken;
import java.util.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<PasswordResetToken> findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(UUID userId);

  Optional<PasswordResetToken> findTopByUserIdOrderByCreatedAtDesc(UUID userId);

  long countByUserIdAndCreatedAtAfter(UUID userId, java.time.Instant after);
}
