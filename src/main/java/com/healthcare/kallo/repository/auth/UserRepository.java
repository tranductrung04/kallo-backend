package com.healthcare.kallo.repository.auth;

import com.healthcare.kallo.model.auth.User;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;
import com.healthcare.kallo.model.auth.enums.UserStatus;

public interface UserRepository extends JpaRepository<User, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.id = :id")
  Optional<User> findByIdForUpdate(@Param("id") UUID id);
  @Query("""
      select u from User u
      where u.status = :status
        and u.emailVerified = true
        and exists (select s.userId from NotificationSettings s
                    where s.userId = u.id
                      and (s.waterReminderEnabled = true or s.mealReminderEnabled = true))
        and exists (select d.id from DeviceToken d where d.userId = u.id)
      order by u.id
      """)
  List<User> findReminderCandidates(@Param("status") UserStatus status, Pageable pageable);
  @Query("select u from User u where lower(u.email) = :email")
  Optional<User> findByNormalizedEmail(@Param("email") String email);

  @Query("select count(u) > 0 from User u where lower(u.email) = :email")
  boolean existsByNormalizedEmail(@Param("email") String email);

  Optional<User> findByGoogleSub(String googleSub);

  List<User> findByIdIn(Collection<UUID> ids);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query("update User u set u.displayName = :displayName, u.timezone = :timezone, u.updatedAt = :now where u.id = :id")
  int updateProfile(
      @Param("id") UUID id, @Param("displayName") String displayName,
      @Param("timezone") String timezone, @Param("now") Instant now);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query("update User u set u.displayName = :displayName, u.timezone = :timezone, u.heightCm = :heightCm, u.gender = :gender, u.birthDate = :birthDate, u.targetWeightKg = :targetWeightKg, u.stepGoal = :stepGoal, u.updatedAt = :now where u.id = :id")
  int updateProfileBody(
      @Param("id") UUID id,
      @Param("displayName") String displayName,
      @Param("timezone") String timezone,
      @Param("heightCm") java.math.BigDecimal heightCm,
      @Param("gender") String gender,
      @Param("birthDate") java.time.LocalDate birthDate,
      @Param("targetWeightKg") java.math.BigDecimal targetWeightKg,
      @Param("stepGoal") Integer stepGoal,
      @Param("now") Instant now);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query("update User u set u.displayName = :displayName, u.timezone = :timezone, u.allergies = :allergies, u.updatedAt = :now where u.id = :id")
  int updateProfileWithAllergies(
      @Param("id") UUID id, @Param("displayName") String displayName,
      @Param("timezone") String timezone, @Param("allergies") List<String> allergies,
      @Param("now") Instant now);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query("update User u set u.displayName = :displayName, u.timezone = :timezone, u.allergies = :allergies, u.heightCm = :heightCm, u.gender = :gender, u.birthDate = :birthDate, u.targetWeightKg = :targetWeightKg, u.stepGoal = :stepGoal, u.updatedAt = :now where u.id = :id")
  int updateProfileWithBodyAndAllergies(
      @Param("id") UUID id, @Param("displayName") String displayName,
      @Param("timezone") String timezone, @Param("allergies") List<String> allergies,
      @Param("heightCm") java.math.BigDecimal heightCm, @Param("gender") String gender,
      @Param("birthDate") java.time.LocalDate birthDate,
      @Param("targetWeightKg") java.math.BigDecimal targetWeightKg,
      @Param("stepGoal") Integer stepGoal, @Param("now") Instant now);

  @Modifying
  @Transactional
  @Query("update User u set u.avatarUrl = :avatarUrl, u.updatedAt = :now where u.id = :id")
  int updateAvatarKey(
      @Param("id") UUID id, @Param("avatarUrl") String avatarUrl, @Param("now") Instant now);
}
