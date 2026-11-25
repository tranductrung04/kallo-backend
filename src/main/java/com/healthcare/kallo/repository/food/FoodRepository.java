package com.healthcare.kallo.repository.food;

import com.healthcare.kallo.model.food.Food;
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

  Optional<Food> findByBarcodeAndDeletedAtIsNull(String barcode);

  Optional<Food> findByVietfoodClassId(Integer vietfoodClassId);

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
}
