package com.healthcare.kallo.repository.plan;

import com.healthcare.kallo.model.plan.MealPlan;
import com.healthcare.kallo.model.plan.enums.MealPlanStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MealPlanRepository extends JpaRepository<MealPlan, UUID> {

  Optional<MealPlan> findByIdAndUserId(UUID id, UUID userId);

  @Query("SELECT p FROM MealPlan p WHERE p.user.id = :userId ORDER BY p.planDate DESC, p.createdAt DESC")
  Page<MealPlan> findByUserIdOrderByPlanDateDesc(@Param("userId") UUID userId, Pageable pageable);

  @Query("SELECT p FROM MealPlan p WHERE p.user.id = :userId AND p.status = :status ORDER BY p.planDate DESC, p.createdAt DESC")
  Page<MealPlan> findByUserIdAndStatusOrderByPlanDateDesc(
      @Param("userId") UUID userId,
      @Param("status") MealPlanStatus status,
      Pageable pageable);

  Optional<MealPlan> findFirstByUserIdAndPlanDateAndStatus(UUID userId, LocalDate planDate, MealPlanStatus status);

  @Modifying
  @Query("""
      UPDATE MealPlan p
      SET p.status = com.healthcare.kallo.model.plan.enums.MealPlanStatus.APPLIED,
          p.appliedAt = :appliedAt,
          p.appliedDate = :appliedDate
      WHERE p.id = :planId AND p.user.id = :userId AND p.status = com.healthcare.kallo.model.plan.enums.MealPlanStatus.SAVED
      """)
  int markPlanAppliedAtomically(
      @Param("planId") UUID planId,
      @Param("userId") UUID userId,
      @Param("appliedAt") Instant appliedAt,
      @Param("appliedDate") LocalDate appliedDate);
}
