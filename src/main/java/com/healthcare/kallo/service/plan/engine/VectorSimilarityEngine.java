package com.healthcare.kallo.service.plan.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import org.springframework.stereotype.Component;

/**
 * 12-dimensional vector similarity, clinical masking, and online preference adaptation engine for
 * MEAL_PLAN recommendations.
 */
@Component
public class VectorSimilarityEngine {

  public static final int VECTOR_DIM = 12;
  public static final double LEARNING_RATE = 0.05;
  public static final double NEUTRAL_SIMILARITY = 0.5;

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

  public double[] normalizePreference(List<Double> raw) {
    return normalize(raw, 0.5);
  }

  public double[] normalizeFoodVector(List<Double> raw) {
    return normalize(raw, 0.0);
  }

  public List<Double> toList(double[] vec) {
    List<Double> list = new ArrayList<>(VECTOR_DIM);
    for (int i = 0; i < VECTOR_DIM; i++) {
      double val = vec != null && i < vec.length ? clamp01(vec[i]) : 0.5;
      list.add(Math.round(val * 10000.0) / 10000.0);
    }
    return list;
  }

  public boolean isZeroVector(double[] vec) {
    if (vec == null || vec.length == 0) {
      return true;
    }
    double normSq = 0.0;
    for (double v : vec) {
      normSq += v * v;
    }
    return normSq < 1e-12;
  }

  public double[] applyClinicalMasks(double[] rawPref, Set<String> conditionCodes) {
    double[] effective = new double[VECTOR_DIM];
    for (int i = 0; i < VECTOR_DIM; i++) {
      effective[i] = rawPref != null && i < rawPref.length ? clamp01(rawPref[i]) : 0.5;
    }
    if (conditionCodes == null || conditionCodes.isEmpty()) {
      return effective;
    }

    boolean diabetes = hasDiabetes(conditionCodes);
    if (diabetes) {
      effective[IDX_SWEET] *= 0.0;
      effective[IDX_FRIED_FATTY] *= 0.3;
    }
    if (containsCode(conditionCodes, "HYPERTENSION")) {
      effective[IDX_FRIED_FATTY] *= 0.3;
      effective[IDX_SOUP_BASED] *= 0.6;
    }
    if (containsCode(conditionCodes, "GOUT")) {
      effective[IDX_SEAFOOD] *= 0.1;
      effective[IDX_BEEF] *= 0.3;
      effective[IDX_PORK] *= 0.4;
    }
    if (containsCode(conditionCodes, "CHRONIC_KIDNEY_DISEASE")) {
      effective[IDX_HIGH_PROTEIN] *= 0.2;
      effective[IDX_FRIED_FATTY] *= 0.4;
      effective[IDX_SOUP_BASED] *= 0.5;
    }
    return effective;
  }

  public double cosineSimilarity(double[] effectivePref, double[] foodVec) {
    if (isZeroVector(effectivePref) || isZeroVector(foodVec)) {
      return NEUTRAL_SIMILARITY;
    }
    double dot = 0.0;
    double normA = 0.0;
    double normB = 0.0;
    for (int i = 0; i < VECTOR_DIM; i++) {
      double a = effectivePref[i];
      double b = foodVec[i];
      dot += a * b;
      normA += a * a;
      normB += b * b;
    }
    double denom = Math.sqrt(normA) * Math.sqrt(normB);
    if (denom < 1e-12) {
      return NEUTRAL_SIMILARITY;
    }
    return clamp01(dot / denom);
  }

  public List<Double> adaptPreference(List<Double> currentPref, List<Double> loggedFoodVector) {
    double[] current = normalizePreference(currentPref);
    double[] foodVec = normalizeFoodVector(loggedFoodVector);
    if (isZeroVector(foodVec)) {
      return toList(current);
    }
    double[] updated = new double[VECTOR_DIM];
    for (int i = 0; i < VECTOR_DIM; i++) {
      updated[i] = clamp01((1.0 - LEARNING_RATE) * current[i] + LEARNING_RATE * foodVec[i]);
    }
    return toList(updated);
  }

  public boolean hasDiabetes(Set<String> conditionCodes) {
    return containsCode(conditionCodes, "DIABETES")
        || containsCode(conditionCodes, "DIABETES_T1")
        || containsCode(conditionCodes, "DIABETES_T2");
  }

  public String buildMedicalWarning(Set<String> conditionCodes) {
    if (conditionCodes == null || conditionCodes.isEmpty()) {
      return null;
    }
    StringJoiner notes = new StringJoiner(" ");
    if (hasDiabetes(conditionCodes)) {
      notes.add(
          "Hồ sơ có tiểu đường (DIABETES): đã loại món > 10g đường/khẩu phần và giới hạn tổng đường <= 25g/ngày theo rule ứng dụng.");
    }
    if (containsCode(conditionCodes, "HYPERTENSION")) {
      notes.add(
          "Hồ sơ có tăng huyết áp (HYPERTENSION): CSDL chưa có dữ liệu Sodium định lượng, đã giảm ưu tiên món chiên rán/nhiều nước dùng.");
    }
    if (containsCode(conditionCodes, "GOUT")) {
      notes.add(
          "Hồ sơ có bệnh Gút (GOUT): CSDL chưa có dữ liệu Purine định lượng, đã giảm ưu tiên hải sản và thịt đỏ.");
    }
    if (containsCode(conditionCodes, "CHRONIC_KIDNEY_DISEASE")) {
      notes.add(
          "Hồ sơ có bệnh thận mạn (CHRONIC_KIDNEY_DISEASE): CSDL chưa có Potassium/Phosphorus/Sodium định lượng, đã giảm ưu tiên món quá giàu đạm/chiên rán.");
    }
    String combined = notes.toString();
    if (combined.isBlank()) {
      return null;
    }
    return combined + " Kết quả chỉ mang tính hỗ trợ, không thay thế tư vấn bác sĩ.";
  }

  private static boolean containsCode(Set<String> codes, String target) {
    if (codes == null) {
      return false;
    }
    for (String code : codes) {
      if (code != null && code.trim().toUpperCase(Locale.ROOT).equals(target)) {
        return true;
      }
    }
    return false;
  }

  private static double[] normalize(List<Double> raw, double defaultValue) {
    double[] vec = new double[VECTOR_DIM];
    for (int i = 0; i < VECTOR_DIM; i++) {
      if (raw != null && i < raw.size() && raw.get(i) != null) {
        vec[i] = clamp01(raw.get(i));
      } else {
        vec[i] = defaultValue;
      }
    }
    return vec;
  }

  private static double clamp01(double value) {
    if (Double.isNaN(value) || value < 0.0) {
      return 0.0;
    }
    return Math.min(1.0, value);
  }
}
