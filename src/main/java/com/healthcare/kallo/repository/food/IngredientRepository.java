package com.healthcare.kallo.repository.food;

import com.healthcare.kallo.model.food.Ingredient;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, UUID> {

  @Query("SELECT i FROM Ingredient i WHERE i.isAllergen = true AND i.ownerUserId IS NULL AND i.isActive = true ORDER BY i.name ASC")
  List<Ingredient> findAllCommonAllergens();

  @Query("SELECT i FROM Ingredient i WHERE i.id IN :ids")
  List<Ingredient> findAllByIds(@Param("ids") Collection<UUID> ids);

  @Query(value = """
      SELECT * FROM ingredients i
      WHERE i.is_active = true
        AND (i.owner_user_id IS NULL OR i.owner_user_id = :userId)
        AND (:allergenOnly = false OR (i.is_allergen = true AND i.owner_user_id IS NULL))
        AND (
          CAST(:q AS text) IS NULL OR :q = ''
          OR i.name_unaccented ILIKE concat('%', immutable_unaccent(lower(CAST(:q AS text))), '%')
          OR word_similarity(immutable_unaccent(lower(CAST(:q AS text))), i.name_unaccented) > 0.3
        )
      ORDER BY
        CASE WHEN :q IS NOT NULL AND :q <> ''
             THEN similarity(i.name_unaccented, immutable_unaccent(lower(CAST(:q AS text))))
             ELSE 0.0 END DESC,
        i.name ASC
      """, nativeQuery = true)
  List<Ingredient> searchIngredients(
      @Param("userId") UUID userId,
      @Param("q") String q,
      @Param("allergenOnly") boolean allergenOnly);

  Optional<Ingredient> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);

  @Query("SELECT count(i) FROM Ingredient i WHERE i.ownerUserId = :userId AND i.createdAt >= :since")
  long countByOwnerUserIdAndCreatedAtAfter(@Param("userId") UUID userId, @Param("since") Instant since);
}
