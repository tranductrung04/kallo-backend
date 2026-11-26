package com.healthcare.kallo.repository.nutri;

import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.model.nutri.UserFoodLog;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserFoodLogRepository extends JpaRepository<UserFoodLog, UUID> {

  interface DailyFoodTotals {
    BigDecimal getCalories();

    BigDecimal getProteinG();

    BigDecimal getCarbsG();

    BigDecimal getFatG();

    BigDecimal getSugarG();

    BigDecimal getFiberG();

    int getMealsLoggedCount();
  }

  Optional<UserFoodLog> findByUserIdAndClientUuid(UUID userId, UUID clientUuid);

  Optional<UserFoodLog> findByIdAndUserId(UUID id, UUID userId);

  Optional<UserFoodLog> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

  List<UserFoodLog> findByUserIdAndLoggedDateAndDeletedAtIsNullOrderByLoggedAtAsc(
      UUID userId, LocalDate loggedDate);

  List<UserFoodLog> findByUserIdAndLoggedDateAndMealTypeAndDeletedAtIsNullOrderByLoggedAtAsc(
      UUID userId, LocalDate loggedDate, MealType mealType);

  boolean existsByUserIdAndLoggedDateAndMealTypeAndDeletedAtIsNull(
      UUID userId, LocalDate loggedDate, MealType mealType);

  @Query("""
      SELECT l.loggedAt FROM UserFoodLog l
      WHERE l.userId = :userId
        AND l.mealType = :mealType
        AND l.deletedAt IS NULL
        AND l.loggedAt >= :since
      ORDER BY l.loggedAt ASC
      """)
  List<Instant> findRecentLoggedAtByMealType(
      @Param("userId") UUID userId,
      @Param("mealType") MealType mealType,
      @Param("since") Instant since);

  @Query(value = """
      SELECT COALESCE(SUM(calories), 0) AS calories,
             COALESCE(SUM(protein_g), 0) AS proteinG,
             COALESCE(SUM(carbs_g), 0) AS carbsG,
             COALESCE(SUM(fat_g), 0) AS fatG,
             COALESCE(SUM(sugar_g), 0) AS sugarG,
             COALESCE(SUM(fiber_g), 0) AS fiberG,
             COUNT(DISTINCT meal_type) AS mealsLoggedCount
      FROM user_food_logs
      WHERE user_id = :userId
        AND logged_date = :loggedDate
        AND deleted_at IS NULL
      """, nativeQuery = true)
  DailyFoodTotals aggregateDailyTotals(
      @Param("userId") UUID userId,
      @Param("loggedDate") LocalDate loggedDate);

  @Query(value = """
      SELECT f.id
      FROM (
          SELECT ufl.food_id, MAX(ufl.logged_at) AS last_logged_at
          FROM user_food_logs ufl
          WHERE ufl.user_id = :userId
            AND ufl.deleted_at IS NULL
            AND ufl.food_id IS NOT NULL
          GROUP BY ufl.food_id
      ) recent
      JOIN foods f ON f.id = recent.food_id
      WHERE f.deleted_at IS NULL
        AND (f.owner_user_id IS NULL OR f.owner_user_id = :userId)
      ORDER BY recent.last_logged_at DESC
      LIMIT :limit
      """, nativeQuery = true)
  List<UUID> findRecentDistinctFoodIds(
      @Param("userId") UUID userId,
      @Param("limit") int limit);

  @Modifying
  @Query(value = """
      INSERT INTO user_food_logs (
          id, user_id, food_id, meal_type, serving_name, serving_grams, serving_quantity,
          source, logged_date, logged_at, image_key,
          calories, protein_g, carbs_g, fat_g, sugar_g, fiber_g,
          ai_predicted_class_id, feedback_given, user_consented_feedback,
          client_uuid, created_at, updated_at
      ) VALUES (
          :id, :userId, :foodId, :mealType, :servingName, :servingGrams, :servingQuantity,
          :source, :loggedDate, :loggedAt, :imageKey,
          :calories, :proteinG, :carbsG, :fatG, :sugarG, :fiberG,
          :aiPredictedClassId, false, false,
          :clientUuid, :createdAt, :updatedAt
      )
      ON CONFLICT (user_id, client_uuid) DO NOTHING
      """, nativeQuery = true)
  int insertIfAbsent(
      @Param("id") UUID id,
      @Param("userId") UUID userId,
      @Param("foodId") UUID foodId,
      @Param("mealType") String mealType,
      @Param("servingName") String servingName,
      @Param("servingGrams") BigDecimal servingGrams,
      @Param("servingQuantity") BigDecimal servingQuantity,
      @Param("source") String source,
      @Param("loggedDate") LocalDate loggedDate,
      @Param("loggedAt") Instant loggedAt,
      @Param("imageKey") String imageKey,
      @Param("calories") BigDecimal calories,
      @Param("proteinG") BigDecimal proteinG,
      @Param("carbsG") BigDecimal carbsG,
      @Param("fatG") BigDecimal fatG,
      @Param("sugarG") BigDecimal sugarG,
      @Param("fiberG") BigDecimal fiberG,
      @Param("aiPredictedClassId") Integer aiPredictedClassId,
      @Param("clientUuid") UUID clientUuid,
      @Param("createdAt") Instant createdAt,
      @Param("updatedAt") Instant updatedAt);

  @Modifying
  @Query(value = """
      INSERT INTO user_food_logs (
          id, user_id, serving_quantity, source, logged_at,
          protein_g, carbs_g, fat_g, sugar_g, fiber_g,
          feedback_given, user_consented_feedback,
          client_uuid, created_at, updated_at, deleted_at
      ) VALUES (
          :id, :userId, 1.00, 'SEARCH', :deletedAt,
          0.0, 0.0, 0.0, 0.0, 0.0,
          false, false,
          :clientUuid, :deletedAt, :updatedAt, :deletedAt
      )
      ON CONFLICT (user_id, client_uuid) DO NOTHING
      """, nativeQuery = true)
  int insertTombstoneIfAbsent(
      @Param("id") UUID id,
      @Param("userId") UUID userId,
      @Param("clientUuid") UUID clientUuid,
      @Param("updatedAt") Instant updatedAt,
      @Param("deletedAt") Instant deletedAt);

  @Query("""
      SELECT l FROM UserFoodLog l
      WHERE l.userId = :userId
        AND l.syncSeq > :sinceSeq
        AND l.serverUpdatedAt <= :settledBefore
      ORDER BY l.syncSeq ASC
      """)
  List<UserFoodLog> findSyncChanges(
      @Param("userId") UUID userId,
      @Param("sinceSeq") long sinceSeq,
      @Param("settledBefore") Instant settledBefore,
      Pageable pageable);

  @Modifying
  @Query("DELETE FROM UserFoodLog l WHERE l.deletedAt IS NOT NULL AND l.deletedAt < :cutoff")
  int deleteTombstonesBefore(@Param("cutoff") Instant cutoff);

  @Query("""
      SELECT DISTINCT l.foodId FROM UserFoodLog l
      WHERE l.userId = :userId
        AND l.deletedAt IS NULL
        AND l.foodId IS NOT NULL
        AND l.loggedDate >= :sinceDate
      """)
  List<UUID> findLoggedFoodIdsSince(
      @Param("userId") UUID userId, @Param("sinceDate") LocalDate sinceDate);
}
