package com.healthcare.kallo.repository.food;

import com.healthcare.kallo.model.food.Food;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FoodRepository extends JpaRepository<Food, UUID> {

  interface FoodSuggestionCandidate {
    UUID getId();
    String getName();
    String getNameUnaccented();
    Integer getCaloriesPerServing();
  }

  @Query("""
      SELECT f.id AS id, f.name AS name, f.nameUnaccented AS nameUnaccented, f.caloriesPerServing AS caloriesPerServing
      FROM Food f
      WHERE f.deletedAt IS NULL
        AND (f.ownerUserId IS NULL OR f.ownerUserId = :userId)
      """)
  List<FoodSuggestionCandidate> findActiveSuggestionCandidates(@Param("userId") UUID userId);

  Optional<Food> findByBarcodeAndDeletedAtIsNull(String barcode);

  Optional<Food> findByVietfoodClassId(Integer vietfoodClassId);

  @Query("""
      SELECT f FROM Food f
      WHERE f.deletedAt IS NULL
        AND f.ownerUserId IS NULL
        AND f.nameUnaccented IN :namesUnaccented
      ORDER BY
        CASE WHEN f.source = com.healthcare.kallo.model.food.enums.FoodSource.SYSTEM THEN 0 ELSE 1 END,
        CASE WHEN f.vietfoodClassId IS NOT NULL THEN 0 ELSE 1 END,
        f.name ASC
      """)
  List<Food> findActiveSystemFoodsByNameUnaccentedIn(
      @Param("namesUnaccented") Collection<String> namesUnaccented);

  @Query(value = """
      SELECT f.* FROM foods f
      WHERE f.deleted_at IS NULL
        AND f.owner_user_id IS NULL
        AND (
          f.name_unaccented = :label
          OR f.name_unaccented LIKE concat(:label, ' %')
          OR :label LIKE concat(f.name_unaccented, ' %')
          OR similarity(f.name_unaccented, :label) >= 0.45
        )
      ORDER BY
        CASE WHEN f.name_unaccented = :label THEN 0 ELSE 1 END,
        CASE WHEN f.vietfood_class_id IS NOT NULL THEN 0 ELSE 1 END,
        similarity(f.name_unaccented, :label) DESC,
        length(f.name_unaccented) ASC
      LIMIT 1
      """, nativeQuery = true)
  Optional<Food> findBestSystemMatchByUnaccentedLabel(@Param("label") String label);

  @Query("""
      SELECT f FROM Food f
      LEFT JOIN FETCH f.ingredients fi
      LEFT JOIN FETCH fi.ingredient
      WHERE f.id = :id AND f.deletedAt IS NULL
      """)
  Optional<Food> findByIdWithIngredients(@Param("id") UUID id);

  @Query(value = """
      SELECT * FROM foods f
      WHERE f.deleted_at IS NULL
        AND (f.owner_user_id IS NULL OR f.owner_user_id = :userId)
        AND (CAST(:category AS text) IS NULL OR f.category = CAST(:category AS text))
        AND (
          CAST(:q AS text) IS NULL OR :q = ''
          OR f.name_unaccented ILIKE concat('%', immutable_unaccent(lower(CAST(:q AS text))), '%')
          OR word_similarity(immutable_unaccent(lower(CAST(:q AS text))), f.name_unaccented) > 0.3
        )
      ORDER BY
        CASE WHEN :q IS NOT NULL AND :q <> ''
             THEN similarity(f.name_unaccented, immutable_unaccent(lower(CAST(:q AS text))))
             ELSE 0.0 END DESC,
        f.name ASC
      """,
      countQuery = """
      SELECT count(*) FROM foods f
      WHERE f.deleted_at IS NULL
        AND (f.owner_user_id IS NULL OR f.owner_user_id = :userId)
        AND (CAST(:category AS text) IS NULL OR f.category = CAST(:category AS text))
        AND (
          CAST(:q AS text) IS NULL OR :q = ''
          OR f.name_unaccented ILIKE concat('%', immutable_unaccent(lower(CAST(:q AS text))), '%')
          OR word_similarity(immutable_unaccent(lower(CAST(:q AS text))), f.name_unaccented) > 0.3
        )
      """,
      nativeQuery = true)
  Page<Food> searchFoods(
      @Param("userId") UUID userId,
      @Param("q") String q,
      @Param("category") String category,
      Pageable pageable);

  @Query("""
      SELECT f FROM Food f
      JOIN f.ingredients fi
      WHERE fi.ingredient.id = :ingredientId
        AND f.ownerUserId = :ownerUserId
        AND f.deletedAt IS NULL
      """)
  List<Food> findAllCustomRecipesByIngredientAndOwner(
      @Param("ingredientId") UUID ingredientId,
      @Param("ownerUserId") UUID ownerUserId);

  @Query(value = """
      SELECT f.*
      FROM foods f
      WHERE f.deleted_at IS NULL
        AND (
            f.source = 'SYSTEM'
            OR (:includeCustomFoods = TRUE AND f.source = 'USER_CUSTOM' AND f.owner_user_id = :userId)
        )
        AND (:hasExcludedIds = FALSE OR f.id NOT IN (:excludedFoodIds))
        AND (:hasBudgetLimit = FALSE OR f.reference_price_vnd IS NOT NULL)
        AND NOT EXISTS (
            SELECT 1
            FROM food_ingredients fi
            INNER JOIN ingredients i ON i.id = fi.ingredient_id
            INNER JOIN user_allergies ua ON ua.ingredient_id = i.id
            WHERE fi.food_id = f.id
              AND ua.user_id = :userId
              AND i.is_allergen = TRUE
              AND i.owner_user_id IS NULL
              AND i.is_active = TRUE
        )
      ORDER BY f.name ASC
      """, nativeQuery = true)
  List<Food> findEligibleFoodsForMealPlan(
      @Param("userId") UUID userId,
      @Param("includeCustomFoods") boolean includeCustomFoods,
      @Param("hasExcludedIds") boolean hasExcludedIds,
      @Param("excludedFoodIds") Collection<UUID> excludedFoodIds,
      @Param("hasBudgetLimit") boolean hasBudgetLimit);
}
