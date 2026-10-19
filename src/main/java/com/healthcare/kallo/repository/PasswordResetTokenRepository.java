package com.healthcare.kallo.repository;

import com.healthcare.kallo.model.PasswordResetToken;
import java.util.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
  Optional<PasswordResetToken> findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(UUID userId);
}
