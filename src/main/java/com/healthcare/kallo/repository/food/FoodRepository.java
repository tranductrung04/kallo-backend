package com.healthcare.kallo.repository.food;

import com.healthcare.kallo.model.food.Food;
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

  Optional<Food> findByBarcode(String barcode);

  Optional<Food> findByVietfoodClassId(Integer vietfoodClassId);

  Optional<Food> findByIdAndOwnerId(UUID id, UUID ownerId);

  @Query("SELECT f FROM Food f WHERE ((f.owner IS NULL AND f.isVerified = true) OR (f.owner.id = :userId)) AND f.category <> com.healthcare.kallo.model.food.enums.FoodCategory.NON_FOOD")
  java.util.List<Food> findAvailableFoodsForUser(@Param("userId") UUID userId);

  @Query("SELECT f FROM Food f WHERE f.id = :foodId AND ((f.owner IS NULL AND f.isVerified = true) OR (f.owner.id = :userId))")
  Optional<Food> findVisibleFoodById(@Param("foodId") UUID foodId, @Param("userId") UUID userId);

  @Query(
      value = """
          SELECT * FROM foods f
          WHERE ((f.owner_user_id IS NULL AND f.is_verified = true) OR f.owner_user_id = :userId)
            AND (CAST(:category AS text) IS NULL OR f.category = CAST(:category AS text))
            AND (
              CAST(:query AS text) IS NULL OR CAST(:query AS text) = '' OR
              immutable_unaccent(lower(f.name)) ILIKE CONCAT('%', immutable_unaccent(lower(CAST(:query AS text))), '%') OR
              immutable_unaccent(lower(f.name)) % immutable_unaccent(lower(CAST(:query AS text)))
            )
          ORDER BY
            CASE WHEN CAST(:query AS text) IS NOT NULL AND CAST(:query AS text) <> ''
                 THEN similarity(immutable_unaccent(lower(f.name)), immutable_unaccent(lower(CAST(:query AS text))))
                 ELSE 0 END DESC,
            f.name ASC
          """,
      countQuery = """
          SELECT count(*) FROM foods f
          WHERE ((f.owner_user_id IS NULL AND f.is_verified = true) OR f.owner_user_id = :userId)
            AND (CAST(:category AS text) IS NULL OR f.category = CAST(:category AS text))
            AND (
              CAST(:query AS text) IS NULL OR CAST(:query AS text) = '' OR
              immutable_unaccent(lower(f.name)) ILIKE CONCAT('%', immutable_unaccent(lower(CAST(:query AS text))), '%') OR
              immutable_unaccent(lower(f.name)) % immutable_unaccent(lower(CAST(:query AS text)))
            )
          """,
      nativeQuery = true)
  Page<Food> searchFoods(
      @Param("userId") UUID userId,
      @Param("query") String query,
      @Param("category") String category,
      Pageable pageable);
}
