package com.healthcare.kallo.repository;

import com.healthcare.kallo.model.User;
import java.util.*;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserRepository extends JpaRepository<User, UUID> {
  @Query("select u from User u where lower(u.email) = :email")
  Optional<User> findByNormalizedEmail(@Param("email") String email);

  @Query("select count(u) > 0 from User u where lower(u.email) = :email")
  boolean existsByNormalizedEmail(@Param("email") String email);

  Optional<User> findByGoogleSub(String googleSub);

  @Modifying
  @Transactional
  @Query("update User u set u.displayName = :displayName, u.timezone = :timezone, u.updatedAt = :now where u.id = :id")
  int updateProfile(
      @Param("id") UUID id,
      @Param("displayName") String displayName,
      @Param("timezone") String timezone,
      @Param("now") Instant now);

  @Modifying
  @Transactional
  @Query("update User u set u.avatarUrl = :avatarUrl, u.updatedAt = :now where u.id = :id")
  int updateAvatarKey(
      @Param("id") UUID id, @Param("avatarUrl") String avatarUrl, @Param("now") Instant now);
}
