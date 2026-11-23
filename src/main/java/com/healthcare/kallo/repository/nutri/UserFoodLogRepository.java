package com.healthcare.kallo.repository.nutri;

import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.nutri.UserFoodLog;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserFoodLogRepository extends JpaRepository<UserFoodLog, UUID> {

  Optional<UserFoodLog> findByUserIdAndClientUuid(UUID userId, UUID clientUuid);

  Optional<UserFoodLog> findByIdAndUserId(UUID id, UUID userId);

  List<UserFoodLog> findByUserIdAndLoggedDateOrderByLoggedAtAsc(UUID userId, LocalDate loggedDate);

  List<UserFoodLog> findByUserIdAndLoggedDate(UUID userId, LocalDate loggedDate);

  @Query("""
      SELECT COUNT(DISTINCT l.mealType)
      FROM UserFoodLog l
      WHERE l.user.id = :userId AND l.loggedDate = :date
      """)
  int countDistinctMealTypes(@Param("userId") UUID userId, @Param("date") LocalDate date);

  @Query("""
      SELECT
        COALESCE(SUM(l.calories), 0),
        COALESCE(SUM(l.proteinG), 0),
        COALESCE(SUM(l.carbsG), 0),
        COALESCE(SUM(l.fatG), 0),
        COALESCE(SUM(l.sugarG), 0),
        COALESCE(SUM(l.fiberG), 0)
      FROM UserFoodLog l
      WHERE l.user.id = :userId AND l.loggedDate = :date
      """)
  List<Object[]> sumNutrientsByDate(@Param("userId") UUID userId, @Param("date") LocalDate date);

  @Query(
      value = """
          SELECT f.*
          FROM foods f
          JOIN (
              SELECT food_id, MAX(logged_at) AS max_logged_at
              FROM user_food_logs
              WHERE user_id = :userId
              GROUP BY food_id
          ) recent ON f.id = recent.food_id
          ORDER BY recent.max_logged_at DESC
          """,
      countQuery = """
          SELECT count(DISTINCT food_id)
          FROM user_food_logs
          WHERE user_id = :userId
          """,
      nativeQuery = true)
  List<Food> findRecentFoods(@Param("userId") UUID userId, Pageable pageable);

  boolean existsByUserIdAndLoggedDateAndMealType(UUID userId, LocalDate date, com.healthcare.kallo.model.food.enums.MealType mealType);

  boolean existsByFoodId(UUID foodId);
}
