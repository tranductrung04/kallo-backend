package com.healthcare.kallo.service.plan.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VectorSimilarityEngineTest {

  private VectorSimilarityEngine engine;

  @BeforeEach
  void setUp() {
    engine = new VectorSimilarityEngine();
  }

  @Test
  @DisplayName("Cosine similarity returns 1.0 for identical vectors")
  void cosineSimilarity_identicalVectors_returnsOne() {
    List<Double> v1 = List.of(1.0, 0.0, 0.5, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    List<Double> v2 = List.of(1.0, 0.0, 0.5, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

    double sim = engine.cosineSimilarity(v1, v2);
    assertThat(sim).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.001));
  }

  @Test
  @DisplayName("Cosine similarity returns 0.0 for orthogonal vectors")
  void cosineSimilarity_orthogonalVectors_returnsZero() {
    List<Double> v1 = List.of(1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    List<Double> v2 = List.of(0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

    double sim = engine.cosineSimilarity(v1, v2);
    assertThat(sim).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.001));
  }

  @Test
  @DisplayName("Clinical safety mask zeros out sweet preference for diabetics")
  void applyClinicalSafetyMask_diabetic_zerosSweetPreference() {
    List<Double> pref = List.of(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.8, 0.5, 0.5, 1.0, 0.5);
    List<String> conditions = List.of("DIABETES");

    List<Double> masked = engine.applyClinicalSafetyMask(pref, conditions);

    assertThat(masked.get(VectorSimilarityEngine.IDX_SWEET)).isEqualTo(0.0);
    assertThat(masked.get(VectorSimilarityEngine.IDX_FRIED_FATTY)).isEqualTo(0.4); // halved
  }

  @Test
  @DisplayName("Adaptive online learning shifts preference towards logged food")
  void updatePreferenceAdaptive_shiftsTowardsFood() {
    List<Double> initial = List.of(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5);
    List<Double> food = List.of(1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

    List<Double> updated = engine.updatePreferenceAdaptive(initial, food, 0.1);

    // Index 0: 0.9 * 0.5 + 0.1 * 1.0 = 0.45 + 0.10 = 0.55
    assertThat(updated.get(0)).isCloseTo(0.55, org.assertj.core.data.Offset.offset(0.001));
    // Index 1: 0.9 * 0.5 + 0.1 * 0.0 = 0.45
    assertThat(updated.get(1)).isCloseTo(0.45, org.assertj.core.data.Offset.offset(0.001));
  }

  @Test
  @DisplayName("Zero vector does not decay preference in updatePreferenceAdaptive")
  void updatePreferenceAdaptive_zeroVector_doesNotDecay() {
    List<Double> initial = List.of(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5);
    List<Double> zeroFood = List.of(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

    assertThat(engine.isZeroVector(zeroFood)).isTrue();

    List<Double> updated = engine.updatePreferenceAdaptive(initial, zeroFood, 0.1);

    // Initial preference must remain untouched (0.5), not decayed to 0.45
    assertThat(updated).isEqualTo(initial);
  }
}
