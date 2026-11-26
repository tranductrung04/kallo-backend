package com.healthcare.kallo.repository.plan;

import com.healthcare.kallo.model.plan.MealPlanFood;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MealPlanFoodRepository extends JpaRepository<MealPlanFood, UUID> {

  Optional<MealPlanFood> findByIdAndMealPlanId(UUID id, UUID mealPlanId);
}
