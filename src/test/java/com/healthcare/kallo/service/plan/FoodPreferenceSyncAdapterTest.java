package com.healthcare.kallo.service.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.nutri.enums.LogSource;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.repository.food.FoodRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.plan.engine.VectorSimilarityEngine;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FoodPreferenceSyncAdapterTest {

  @Mock private FoodRepository foodRepository;
  @Mock private UserHealthProfileRepository profileRepository;

  private VectorSimilarityEngine vectorEngine;
  private FoodPreferenceSyncAdapter adapter;
  private UUID userId;
  private UUID foodId;

  @BeforeEach
  void setUp() {
    vectorEngine = new VectorSimilarityEngine();
    adapter = new FoodPreferenceSyncAdapter(foodRepository, profileRepository, vectorEngine);
    userId = UUID.randomUUID();
    foodId = UUID.randomUUID();
  }

  @Test
  void testAdaptiveLearning_PreferenceVectorShifts() {
    UserHealthProfile profile = new UserHealthProfile();
    profile.setUserId(userId);
    when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));

    Food beefDish = new Food();
    beefDish.setFeatureVector(
        List.of(1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.2, 0.8, 0.0, 0.0, 0.8));
    when(foodRepository.findById(foodId)).thenReturn(Optional.of(beefDish));

    for (int i = 0; i < 5; i++) {
      adapter.onFoodLogged(userId, foodId, LogSource.SEARCH);
    }

    assertThat(profile.getPreferenceVector().get(VectorSimilarityEngine.IDX_BEEF))
        .isGreaterThan(0.60);

    // Meal plan applies must not shift user preference vector
    double beefAfter5 = profile.getPreferenceVector().get(VectorSimilarityEngine.IDX_BEEF);
    adapter.onFoodLogged(userId, foodId, LogSource.MEAL_PLAN);
    assertThat(profile.getPreferenceVector().get(VectorSimilarityEngine.IDX_BEEF))
        .isEqualTo(beefAfter5);

    // Zero-vector custom recipe must not pull vector toward 0
    UUID customFoodId = UUID.randomUUID();
    Food customZero = new Food();
    customZero.setFeatureVector(
        List.of(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
    when(foodRepository.findById(customFoodId)).thenReturn(Optional.of(customZero));

    adapter.onFoodLogged(userId, customFoodId, LogSource.CUSTOM);
    assertThat(profile.getPreferenceVector().get(VectorSimilarityEngine.IDX_BEEF))
        .isEqualTo(beefAfter5);
    verify(profileRepository, never()).delete(any());
  }
}
