package com.healthcare.kallo.service.plan;

import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.nutri.enums.LogSource;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.repository.food.FoodRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.nutri.FoodPreferenceSyncPort;
import com.healthcare.kallo.service.plan.engine.VectorSimilarityEngine;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Receives real meal log notifications from NUTRI via FoodPreferenceSyncPort and updates the
 * user's 12D preference vector incrementally. Ignores MEAL_PLAN applies and zero-vector foods.
 */
@Component
public class FoodPreferenceSyncAdapter implements FoodPreferenceSyncPort {

  private final FoodRepository foodRepository;
  private final UserHealthProfileRepository profileRepository;
  private final VectorSimilarityEngine vectorEngine;

  public FoodPreferenceSyncAdapter(
      FoodRepository foodRepository,
      UserHealthProfileRepository profileRepository,
      VectorSimilarityEngine vectorEngine) {
    this.foodRepository = foodRepository;
    this.profileRepository = profileRepository;
    this.vectorEngine = vectorEngine;
  }

  @Override
  @Transactional
  public void onFoodLogged(UUID userId, UUID foodId, LogSource source) {
    if (userId == null || foodId == null || source == LogSource.MEAL_PLAN) {
      return;
    }

    Food food = foodRepository.findById(foodId).orElse(null);
    if (food == null) {
      return;
    }

    double[] foodVec = vectorEngine.normalizeFoodVector(food.getFeatureVector());
    if (vectorEngine.isZeroVector(foodVec)) {
      return;
    }

    UserHealthProfile profile = profileRepository.findById(userId).orElse(null);
    if (profile == null) {
      return;
    }

    List<Double> updated =
        vectorEngine.adaptPreference(profile.getPreferenceVector(), food.getFeatureVector());
    profile.setPreferenceVector(updated);
    profileRepository.save(profile);
  }
}
