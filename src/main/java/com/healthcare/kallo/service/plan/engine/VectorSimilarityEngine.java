package com.healthcare.kallo.service.plan.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class VectorSimilarityEngine {

  public static final int VECTOR_DIMENSION = 12;
  public static final double DEFAULT_LEARNING_RATE = 0.05;

  // Feature vector indices
  public static final int IDX_SOUP_BASED = 0;
  public static final int IDX_DRY_BASED = 1;
  public static final int IDX_BEEF = 2;
  public static final int IDX_PORK = 3;
  public static final int IDX_POULTRY = 4;
  public static final int IDX_SEAFOOD = 5;
  public static final int IDX_VEGETARIAN = 6;
  public static final int IDX_FRIED_FATTY = 7;
  public static final int IDX_STEAMED_BOILED = 8;
  public static final int IDX_SPICY = 9;
  public static final int IDX_SWEET = 10;
  public static final int IDX_HIGH_PROTEIN = 11;

  /**
   * Calculates the Cosine Similarity between user preference vector and food feature vector.
   * Returns a score between 0.0 and 1.0.
   */
  public double cosineSimilarity(List<Double> userVector, List<Double> foodVector) {
    if (userVector == null || foodVector == null || userVector.isEmpty() || foodVector.isEmpty()) {
      return 0.5; // neutral fallback
    }

    int dim = Math.min(userVector.size(), foodVector.size());
    double dotProduct = 0.0;
    double normA = 0.0;
    double normB = 0.0;

    for (int i = 0; i < dim; i++) {
      double a = userVector.get(i) != null ? userVector.get(i) : 0.0;
      double b = foodVector.get(i) != null ? foodVector.get(i) : 0.0;

      dotProduct += a * b;
      normA += a * a;
      normB += b * b;
    }

    if (normA <= 0.0 || normB <= 0.0) {
      return 0.5;
    }

    double sim = dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    return Math.max(0.0, Math.min(1.0, sim));
  }

  /**
   * Applies clinical safety masks based on user's chronic conditions (e.g. DIABETES, HYPERTENSION).
   * Suppresses adverse food attributes (e.g., zeroing out SWEET for diabetics).
   */
  public List<Double> applyClinicalSafetyMask(List<Double> preferenceVector, List<String> chronicConditions) {
    List<Double> masked = new ArrayList<>(
        preferenceVector != null && !preferenceVector.isEmpty()
            ? preferenceVector
            : defaultPreferenceVector()
    );

    while (masked.size() < VECTOR_DIMENSION) {
      masked.add(0.5);
    }

    if (chronicConditions == null || chronicConditions.isEmpty()) {
      return masked;
    }

    for (String rawCondition : chronicConditions) {
      if (rawCondition == null) continue;
      String condition = rawCondition.trim().toUpperCase();

      if (condition.contains("DIABETES") || condition.contains("TIEU_DUONG")) {
        // Suppress sweet preference to 0.0
        masked.set(IDX_SWEET, 0.0);
        // Reduce fried & fatty tolerance by half
        masked.set(IDX_FRIED_FATTY, masked.get(IDX_FRIED_FATTY) * 0.5);
      }

      if (condition.contains("HYPERTENSION") || condition.contains("HUYET_AP")) {
        // High sodium often in soups and fried foods
        masked.set(IDX_SOUP_BASED, masked.get(IDX_SOUP_BASED) * 0.5);
        masked.set(IDX_SPICY, masked.get(IDX_SPICY) * 0.2);
        masked.set(IDX_FRIED_FATTY, masked.get(IDX_FRIED_FATTY) * 0.5);
      }
    }

    return masked;
  }

  /**
   * Dynamically updates the user's preference vector with adaptive online learning rate gamma.
   * P_t = (1 - gamma) * P_(t-1) + gamma * F_logged
   */
  public List<Double> updatePreferenceAdaptive(
      List<Double> currentPreference,
      List<Double> loggedFoodFeature,
      double learningRate) {

    List<Double> base = new ArrayList<>(
        currentPreference != null && !currentPreference.isEmpty()
            ? currentPreference
            : defaultPreferenceVector()
    );

    while (base.size() < VECTOR_DIMENSION) {
      base.add(0.5);
    }

    if (loggedFoodFeature == null || loggedFoodFeature.isEmpty() || isZeroVector(loggedFoodFeature)) {
      return base;
    }

    List<Double> updated = new ArrayList<>(VECTOR_DIMENSION);
    for (int i = 0; i < VECTOR_DIMENSION; i++) {
      double p = base.get(i);
      double f = i < loggedFoodFeature.size() && loggedFoodFeature.get(i) != null
          ? loggedFoodFeature.get(i)
          : 0.0;

      double next = (1.0 - learningRate) * p + learningRate * f;
      updated.add(Math.max(0.0, Math.min(1.0, next)));
    }

    return updated;
  }

  public List<Double> defaultPreferenceVector() {
    return new ArrayList<>(Collections.nCopies(VECTOR_DIMENSION, 0.5));
  }

  public List<Double> defaultFoodFeatureVector() {
    return new ArrayList<>(Collections.nCopies(VECTOR_DIMENSION, 0.0));
  }

  public boolean isZeroVector(List<Double> vector) {
    if (vector == null || vector.isEmpty()) {
      return true;
    }
    for (Double d : vector) {
      if (d != null && Math.abs(d) > 1e-5) {
        return false;
      }
    }
    return true;
  }
}
