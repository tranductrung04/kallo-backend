package com.healthcare.kallo.service.plan.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VectorSimilarityEngineTest {

  private VectorSimilarityEngine engine;

  @BeforeEach
  void setUp() {
    engine = new VectorSimilarityEngine();
  }

  @Test
  void returnsNeutralSimilarityForZeroVectors() {
    double[] pref = new double[] {1, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 1};
    double[] zero = new double[VectorSimilarityEngine.VECTOR_DIM];

    assertThat(engine.cosineSimilarity(pref, zero)).isEqualTo(0.5);
    assertThat(engine.cosineSimilarity(zero, pref)).isEqualTo(0.5);
  }

  @Test
  void appliesClinicalMasksAndGeneratesTransparentMedicalWarnings() {
    double[] pref = new double[] {0.8, 0.5, 0.9, 0.8, 0.5, 0.9, 0.5, 0.9, 0.6, 0.5, 1.0, 0.9};

    double[] masked =
        engine.applyClinicalMasks(pref, Set.of("DIABETES_T2", "GOUT", "HYPERTENSION", "CHRONIC_KIDNEY_DISEASE"));

    assertThat(masked[VectorSimilarityEngine.IDX_SWEET]).isZero();
    assertThat(masked[VectorSimilarityEngine.IDX_SEAFOOD]).isLessThan(0.15);
    assertThat(masked[VectorSimilarityEngine.IDX_BEEF]).isLessThan(0.35);
    assertThat(masked[VectorSimilarityEngine.IDX_HIGH_PROTEIN]).isLessThan(0.25);
    assertThat(engine.buildMedicalWarning(Set.of("DIABETES_T2", "GOUT")))
        .contains("DIABETES")
        .contains("Purine");
  }

  @Test
  void skipsZeroVectorDuringAdaptivePreferenceLearning() {
    List<Double> initial = engine.toList(engine.normalizePreference(null));
    List<Double> zeroFood = List.of(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

    List<Double> unchanged = engine.adaptPreference(initial, zeroFood);
    assertThat(unchanged).isEqualTo(initial);

    List<Double> beefSoup = List.of(1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0);
    List<Double> shifted = engine.adaptPreference(initial, beefSoup);
    assertThat(shifted.get(VectorSimilarityEngine.IDX_BEEF)).isEqualTo(0.525);
  }
}
