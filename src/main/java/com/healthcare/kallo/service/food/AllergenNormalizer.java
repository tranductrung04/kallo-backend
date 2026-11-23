package com.healthcare.kallo.service.food;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AllergenNormalizer {

  private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

  private static final Map<String, List<String>> THESAURUS = new LinkedHashMap<>();

  static {
    // CRUSTACEANS
    register("CRUSTACEANS", "crustaceans", "tom", "tôm", "cua", "ghe", "ghẹ", "tep", "tép", "be be", "bề bề", "tom hum", "tôm hùm");

    // FISH
    register("FISH", "fish", "ca", "cá", "nuoc mam", "nước mắm", "cha ca", "chả cá");

    // MOLLUSCS
    register("MOLLUSCS", "molluscs", "muc", "mực", "bach tuoc", "bạch tuộc", "so", "sò", "ngheu", "nghêu", "oc", "ốc", "hau", "hàu", "chem chep", "chèm chẹp");

    // SEAFOOD (maps to multiple)
    registerMulti(List.of("CRUSTACEANS", "FISH", "MOLLUSCS"), "seafood", "hai san", "hải sản", "do bien", "đồ biển");

    // PEANUTS
    register("PEANUTS", "peanuts", "peanut", "dau phong", "đậu phộng", "lac", "lạc", "dau phung", "đậu phụng");

    // DAIRY
    register("DAIRY", "dairy", "milk", "sua", "sữa", "bo", "bơ", "pho mai", "phô mai", "cheese");

    // EGGS
    register("EGGS", "eggs", "egg", "trung", "trứng");

    // GLUTEN
    register("GLUTEN", "gluten", "lua mi", "lúa mì", "bot mi", "bột mì", "wheat");

    // SOYBEANS
    register("SOYBEANS", "soybeans", "soy", "dau nanh", "đậu nành", "dau tuong", "đậu tương", "tau hu", "tàu hũ", "dau phu", "đậu phụ");

    // SESAME
    register("SESAME", "sesame", "me", "mè", "vung", "vừng");

    // TREE_NUTS
    register("TREE_NUTS", "tree_nuts", "tree nuts", "hat dieu", "hạt điều", "hanh nhan", "hạnh nhân", "oc cho", "óc chó", "hat mac ca", "hạt mắc ca");
  }

  private static void register(String canonicalTag, String... keywords) {
    for (String kw : keywords) {
      String clean = cleanText(kw);
      THESAURUS.computeIfAbsent(clean, k -> new ArrayList<>()).add(canonicalTag);
    }
  }

  private static void registerMulti(List<String> canonicalTags, String... keywords) {
    for (String kw : keywords) {
      String clean = cleanText(kw);
      THESAURUS.computeIfAbsent(clean, k -> new ArrayList<>()).addAll(canonicalTags);
    }
  }

  public Set<String> normalizeUserAllergies(Collection<String> rawAllergies) {
    if (rawAllergies == null || rawAllergies.isEmpty()) {
      return Collections.emptySet();
    }

    Set<String> result = new HashSet<>();
    for (String raw : rawAllergies) {
      if (raw == null || raw.isBlank()) continue;
      String clean = cleanText(raw);

      // 1. Direct canonical tag match (e.g. "CRUSTACEANS")
      result.add(clean.toUpperCase());

      // 2. Thesaurus keyword mapping
      boolean matched = false;
      for (Map.Entry<String, List<String>> entry : THESAURUS.entrySet()) {
        if (clean.equals(entry.getKey()) || clean.contains(entry.getKey()) || entry.getKey().contains(clean)) {
          result.addAll(entry.getValue());
          matched = true;
        }
      }

      // Also keep unaccented lowercase token as fallback for custom foods
      result.add(clean);
    }
    return result;
  }

  public boolean hasConflict(List<String> foodAllergens, Set<String> normalizedUserAllergies) {
    if (foodAllergens == null || foodAllergens.isEmpty() || normalizedUserAllergies == null || normalizedUserAllergies.isEmpty()) {
      return false;
    }

    for (String fa : foodAllergens) {
      if (fa == null || fa.isBlank()) continue;
      String cleanFa = cleanText(fa);
      String upperFa = cleanFa.toUpperCase();

      // Check if food allergen matches any normalized user tag
      if (normalizedUserAllergies.contains(upperFa)) {
        return true;
      }

      // Substring check on clean text
      for (String userTag : normalizedUserAllergies) {
        if (upperFa.equals(userTag) || cleanFa.contains(userTag) || userTag.contains(cleanFa)) {
          return true;
        }
      }
    }
    return false;
  }

  public static String cleanText(String input) {
    if (input == null) return "";
    String nfd = Normalizer.normalize(input.trim().toLowerCase(), Normalizer.Form.NFD);
    return DIACRITICS_PATTERN.matcher(nfd).replaceAll("").replace('đ', 'd').trim();
  }
}
