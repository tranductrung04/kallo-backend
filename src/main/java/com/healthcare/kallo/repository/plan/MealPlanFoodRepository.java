package com.healthcare.kallo.repository.plan;

import com.healthcare.kallo.model.plan.MealPlanFood;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealPlanFoodRepository extends JpaRepository<MealPlanFood, UUID> {

  Optional<MealPlanFood> findByIdAndMealPlanUserId(UUID id, UUID userId);

  List<MealPlanFood> findByMealPlanId(UUID mealPlanId);

  boolean existsByFoodId(UUID foodId);
}
