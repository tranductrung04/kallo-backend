package com.healthcare.kallo.repository.plan;

import com.healthcare.kallo.model.plan.MealPlan;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MealPlanRepository extends JpaRepository<MealPlan, UUID> {

  Page<MealPlan> findByUserIdOrderByPlanDateDescCreatedAtDesc(UUID userId, Pageable pageable);

  Optional<MealPlan> findByIdAndUserId(UUID id, UUID userId);

  @Query("""
      SELECT DISTINCT p FROM MealPlan p
      LEFT JOIN FETCH p.items i
      WHERE p.id = :id AND p.userId = :userId
      """)
  Optional<MealPlan> findWithItemsByIdAndUserId(
      @Param("id") UUID id, @Param("userId") UUID userId);
}
