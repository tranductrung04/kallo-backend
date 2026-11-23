package com.healthcare.kallo.service.food;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AllergenNormalizerTest {

  private AllergenNormalizer normalizer;

  @BeforeEach
  void setUp() {
    normalizer = new AllergenNormalizer();
  }

  @Test
  @DisplayName("normalizeUserAllergies correctly maps Vietnamese free text 'tôm' to CRUSTACEANS")
  void normalize_tom_crustaceans() {
    Set<String> normalized = normalizer.normalizeUserAllergies(List.of("tôm"));
    assertThat(normalized).contains("CRUSTACEANS");
  }

  @Test
  @DisplayName("normalizeUserAllergies maps 'hải sản' to CRUSTACEANS, FISH, MOLLUSCS")
  void normalize_seafood_multi() {
    Set<String> normalized = normalizer.normalizeUserAllergies(List.of("hải sản"));
    assertThat(normalized).contains("CRUSTACEANS", "FISH", "MOLLUSCS");
  }

  @Test
  @DisplayName("hasConflict catches food tagged with CRUSTACEANS when user enters 'tôm'")
  void conflict_vietnameseFreeText_canonicalTag() {
    Set<String> userAllergies = normalizer.normalizeUserAllergies(List.of("tôm"));
    List<FoodAllergenStub> foodAllergens = List.of();

    boolean conflict = normalizer.hasConflict(List.of("CRUSTACEANS"), userAllergies);
    assertThat(conflict).isTrue();
  }

  @Test
  @DisplayName("hasConflict returns false when food has no conflicting allergens")
  void conflict_noConflict() {
    Set<String> userAllergies = normalizer.normalizeUserAllergies(List.of("tôm"));
    boolean conflict = normalizer.hasConflict(List.of("EGGS", "MILK"), userAllergies);
    assertThat(conflict).isFalse();
  }

  private record FoodAllergenStub(List<String> allergens) {}
}
