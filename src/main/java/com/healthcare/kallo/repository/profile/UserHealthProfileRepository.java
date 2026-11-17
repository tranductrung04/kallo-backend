package com.healthcare.kallo.repository.profile;

import com.healthcare.kallo.model.profile.UserHealthProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserHealthProfileRepository extends JpaRepository<UserHealthProfile, UUID> {

  @Query("SELECT p FROM UserHealthProfile p WHERE p.user.id = :userId")
  Optional<UserHealthProfile> findByUserId(@Param("userId") UUID userId);

  @Query("SELECT COUNT(p) > 0 FROM UserHealthProfile p WHERE p.user.id = :userId")
  boolean existsByUserId(@Param("userId") UUID userId);

  void deleteByUserId(UUID userId);
}
