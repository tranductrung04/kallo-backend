package com.healthcare.kallo.repository.food;

import com.healthcare.kallo.model.food.FoodIngredient;
import com.healthcare.kallo.model.food.FoodIngredientId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FoodIngredientRepository extends JpaRepository<FoodIngredient, FoodIngredientId> {

  List<FoodIngredient> findByFoodId(UUID foodId);

  @Modifying
  @Query("DELETE FROM FoodIngredient fi WHERE fi.food.id = :foodId")
  void deleteByFoodId(@Param("foodId") UUID foodId);
}
