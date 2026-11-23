package com.healthcare.kallo.service.plan.engine;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.model.plan.MealPlan;
import com.healthcare.kallo.model.plan.MealPlanFood;
import com.healthcare.kallo.model.plan.enums.MealPlanStatus;
import com.healthcare.kallo.model.plan.enums.MealPlanType;
import com.healthcare.kallo.service.food.AllergenNormalizer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class RuleBasedMenuGenerator {

  private final VectorSimilarityEngine vectorEngine;
  private final AllergenNormalizer allergenNormalizer;

  private static final BigDecimal[] MULTIPLIERS = new BigDecimal[] {
      BigDecimal.valueOf(0.75),
      BigDecimal.valueOf(1.00),
      BigDecimal.valueOf(1.25),
      BigDecimal.valueOf(1.50)
  };

  public RuleBasedMenuGenerator(VectorSimilarityEngine vectorEngine, AllergenNormalizer allergenNormalizer) {
    this.vectorEngine = vectorEngine;
    this.allergenNormalizer = allergenNormalizer;
  }

  public RuleBasedMenuGenerator(VectorSimilarityEngine vectorEngine) {
    this(vectorEngine, new AllergenNormalizer());
  }

  public record PlanGenerationInput(
      int targetCalories,
      BigDecimal proteinGoalG,
      BigDecimal carbsGoalG,
      BigDecimal fatGoalG,
      List<String> allergies,
      List<String> chronicConditions,
      List<Double> preferenceVector,
      Integer maxBudgetVnd,
      Set<UUID> recentFoodIds,
      List<Food> availableFoods,
      LocalDate planDate,
      Long randomSeed
  ) {}

  public MealPlan generate(PlanGenerationInput input) {
    if (input.targetCalories() <= 0) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mục tiêu calo phải lớn hơn 0");
    }

    if (input.availableFoods() == null || input.availableFoods().isEmpty()) {
      throw new BusinessException(ErrorCode.INSUFFICIENT_FOOD_DATA, "No available food data");
    }

    Random random = input.randomSeed() != null ? new Random(input.randomSeed()) : new Random();

    // 1. PHASE 1: Hard Clinical & Allergy Filtering & Quota Allocation
    List<Food> safeFoods = filterSafeFoods(input.availableFoods(), input.allergies(), input.chronicConditions());
    if (safeFoods.isEmpty()) {
      throw new BusinessException(ErrorCode.INSUFFICIENT_FOOD_DATA, "No food items remain after allergy and medical filtering");
    }

    boolean isDiabetic = isConditionPresent(input.chronicConditions(), "DIABETES") ||
                         isConditionPresent(input.chronicConditions(), "TIEU_DUONG");
    boolean isGout = isConditionPresent(input.chronicConditions(), "GOUT");
    boolean isKidney = isConditionPresent(input.chronicConditions(), "KIDNEY") ||
                       isConditionPresent(input.chronicConditions(), "THAN") ||
                       isConditionPresent(input.chronicConditions(), "CHRONIC_KIDNEY_DISEASE");
    boolean isHypertension = isConditionPresent(input.chronicConditions(), "HYPERTENSION") ||
                            isConditionPresent(input.chronicConditions(), "HUYET_AP");

    List<Double> effectivePref = vectorEngine.applyClinicalSafetyMask(input.preferenceVector(), input.chronicConditions());

    int breakfastTarget = (int) Math.round(input.targetCalories() * 0.25);
    int lunchTarget = (int) Math.round(input.targetCalories() * 0.35);
    int dinnerTarget = (int) Math.round(input.targetCalories() * 0.30);
    int snackTarget = Math.max(50, input.targetCalories() - breakfastTarget - lunchTarget - dinnerTarget);

    double totalP = input.proteinGoalG() != null ? input.proteinGoalG().doubleValue() : 0.0;
    double totalC = input.carbsGoalG() != null ? input.carbsGoalG().doubleValue() : 0.0;
    double totalF = input.fatGoalG() != null ? input.fatGoalG().doubleValue() : 0.0;

    record MealMacroTarget(int calo, double protein, double carbs, double fat) {}
    Map<MealType, MealMacroTarget> mealTargets = Map.of(
        MealType.BREAKFAST, new MealMacroTarget(breakfastTarget, totalP * 0.25, totalC * 0.25, totalF * 0.25),
        MealType.LUNCH, new MealMacroTarget(lunchTarget, totalP * 0.35, totalC * 0.35, totalF * 0.35),
        MealType.DINNER, new MealMacroTarget(dinnerTarget, totalP * 0.30, totalC * 0.30, totalF * 0.30),
        MealType.SNACK, new MealMacroTarget(snackTarget, totalP * 0.10, totalC * 0.10, totalF * 0.10)
    );

    Map<MealType, Integer> mealCalorieTargets = Map.of(
        MealType.BREAKFAST, breakfastTarget,
        MealType.LUNCH, lunchTarget,
        MealType.DINNER, dinnerTarget,
        MealType.SNACK, snackTarget
    );

    // 2. PHASE 2: Greedy-Randomized Selection with Cosine Similarity & Intra-day Diversity
    MealPlan plan = new MealPlan();
    plan.setName("Thực đơn dinh dưỡng " + input.planDate());
    plan.setPlanDate(input.planDate());
    plan.setStatus(MealPlanStatus.DRAFT);
    plan.setType(MealPlanType.DAILY);
    plan.setTargetCalories(input.targetCalories());
    plan.setMaxBudgetVnd(input.maxBudgetVnd());

    Map<MealType, List<Food>> candidatePools = new EnumMap<>(MealType.class);
    Set<UUID> chosenTodayIds = new HashSet<>(input.recentFoodIds() != null ? input.recentFoodIds() : Set.of());

    for (MealType mealType : MealType.values()) {
      List<Food> pool = getFoodsForMealType(safeFoods, mealType);
      if (pool.isEmpty()) {
        // Only fallback to foods that have NO explicit restriction to other meal types
        pool = safeFoods.stream()
            .filter(f -> f.getSuitableMealTypes() == null || f.getSuitableMealTypes().isEmpty())
            .toList();
      }
      if (pool.isEmpty()) {
        throw new BusinessException(ErrorCode.INSUFFICIENT_FOOD_DATA,
            "Không đủ món ăn an toàn phù hợp cho bữa " + mealType);
      }
      candidatePools.put(mealType, pool);

      // Prioritize intra-day variety: pick items not yet chosen today
      List<Food> diversePool = pool.stream()
          .filter(f -> !chosenTodayIds.contains(f.getId()))
          .toList();
      List<Food> effectivePool = diversePool.isEmpty() ? pool : diversePool;

      MealMacroTarget target = mealTargets.get(mealType);
      Food selectedFood = pickBestCandidate(
          effectivePool,
          target.calo(),
          target.protein(),
          target.carbs(),
          target.fat(),
          effectivePref,
          input.recentFoodIds(),
          random
      );
      chosenTodayIds.add(selectedFood.getId());

      MealPlanFood item = buildPlanFood(selectedFood, mealType, BigDecimal.ONE);
      plan.addItem(item);
    }

    // 3. PHASE 3: Local Refinement (1-Swap & Multiplier Tuning)
    refinePlan(plan, input.targetCalories(), mealCalorieTargets, candidatePools, input.maxBudgetVnd(), random);

    // Enforce hard constraints: Budget and Diabetes Sugar Ceiling
    enforceBudgetConstraint(plan, candidatePools, input.maxBudgetVnd());

    if (isDiabetic) {
      enforceHardSugarCeiling(plan, candidatePools, BigDecimal.valueOf(25.0), input.maxBudgetVnd());
    }

    // Re-balance calories to ensure +/- 5% tolerance while preserving budget and sugar limits
    rebalanceCaloriesWithinConstraints(plan, input.targetCalories(), input.maxBudgetVnd(), isDiabetic);

    // Calculate totals and preference match
    calculateTotals(plan, effectivePref);

    List<String> warnings = new ArrayList<>();
    if (isDiabetic) {
      warnings.add("• Tiểu đường (DIABETES): Hệ thống đã áp dụng Chế độ An toàn Y tế: Khống chế tổng lượng đường trong ngày dưới 25g, loại bỏ các món có lượng đường tinh luyện cao và ưu tiên thực phẩm có chỉ số GI thấp.");
    }
    if (isGout) {
      warnings.add("• Bệnh Gout (GOUT): Hệ thống đã loại trừ các món có hàm lượng purin cao (thịt đỏ như thịt bò, hải sản có vỏ, nội tạng động vật) để kiểm soát axit uric máu.");
    }
    if (isKidney) {
      warnings.add("• Bệnh thận mạn (CHRONIC KIDNEY DISEASE): Hệ thống giới hạn lượng đạm (protein) mỗi bữa để giảm áp lực lọc cho thận.");
    }
    if (isHypertension) {
      warnings.add("• Tăng huyết áp (HYPERTENSION): Cơ sở dữ liệu hiện chưa có chỉ số định lượng Natri (muối), do đó hệ thống chưa thể cam kết mức Natri tối ưu tuyệt đối theo chuẩn y khoa. Khuyến cáo người dùng tham khảo chế độ ăn DASH, chủ động nêm nhạt và hạn chế nước chấm mặn.");
    }

    if (!warnings.isEmpty()) {
      plan.setMedicalWarning(String.join("\n", warnings));
    }

    return plan;
  }

  private List<Food> filterSafeFoods(List<Food> foods, List<String> allergies, List<String> chronicConditions) {
    boolean isDiabetic = isConditionPresent(chronicConditions, "DIABETES") ||
                         isConditionPresent(chronicConditions, "TIEU_DUONG");
    boolean isGout = isConditionPresent(chronicConditions, "GOUT");
    boolean isKidney = isConditionPresent(chronicConditions, "KIDNEY") ||
                       isConditionPresent(chronicConditions, "THAN") ||
                       isConditionPresent(chronicConditions, "CHRONIC_KIDNEY_DISEASE");
    boolean isHypertension = isConditionPresent(chronicConditions, "HYPERTENSION") ||
                            isConditionPresent(chronicConditions, "HUYET_AP");

    Set<String> normalizedAllergies = allergenNormalizer.normalizeUserAllergies(allergies);

    List<Food> safe = new ArrayList<>();
    for (Food f : foods) {
      // Exclude non-food items
      if (f.getCategory() == FoodCategory.NON_FOOD) {
        continue;
      }

      // Check allergies
      if (hasAllergyConflict(f, normalizedAllergies)) {
        continue;
      }

      // Check clinical diabetes rules (Free sugar ceiling)
      if (isDiabetic) {
        BigDecimal sugarPerServing = f.getSugarPerServing() != null ? f.getSugarPerServing() : BigDecimal.ZERO;
        BigDecimal sugarPer100g = f.getSugarPer100g() != null ? f.getSugarPer100g() : BigDecimal.ZERO;

        if (sugarPerServing.compareTo(BigDecimal.valueOf(10.0)) > 0 ||
            sugarPer100g.compareTo(BigDecimal.valueOf(15.0)) > 0) {
          continue; // exclude high-sugar food
        }
      }

      // Check Gout purine rules
      if (isGout && hasPurineConflict(f)) {
        continue;
      }

      // Check Chronic Kidney Disease protein rules
      if (isKidney && hasCkdConflict(f)) {
        continue;
      }

      // Check Hypertension processed/salty foods
      if (isHypertension && hasHypertensionConflict(f)) {
        continue;
      }

      safe.add(f);
    }
    return safe;
  }

  private boolean hasPurineConflict(Food food) {
    if (food.getAllergens() != null) {
      for (String a : food.getAllergens()) {
        if (a == null) continue;
        String upper = a.toUpperCase();
        if (upper.contains("BEEF") || upper.contains("SHELLFISH") || upper.contains("SEAFOOD")
            || upper.contains("CRUSTACEAN") || upper.contains("SHRIMP")) {
          return true;
        }
      }
    }
    String name = food.getNameUnaccented() != null ? food.getNameUnaccented().toLowerCase() : "";
    return name.contains("thit bo") || name.contains("long") || name.contains("noi tang")
        || name.contains("gan") || name.contains("hai san") || name.contains("muc")
        || name.contains("tom") || name.contains("cua") || name.contains("oc");
  }

  private boolean hasCkdConflict(Food food) {
    BigDecimal protein = food.getProteinPerServing() != null ? food.getProteinPerServing() : BigDecimal.ZERO;
    return protein.compareTo(BigDecimal.valueOf(25.0)) > 0;
  }

  private boolean hasHypertensionConflict(Food food) {
    String name = food.getNameUnaccented() != null ? food.getNameUnaccented().toLowerCase() : "";
    return name.contains("muoi") || name.contains("mam") || name.contains("dua chua")
        || name.contains("ca phao") || name.contains("kho qua man") || name.contains("mi goi")
        || name.contains("lap xuong");
  }

  private boolean hasAllergyConflict(Food food, Set<String> normalizedAllergies) {
    if (normalizedAllergies == null || normalizedAllergies.isEmpty()) return false;
    return allergenNormalizer.hasConflict(food.getAllergens(), normalizedAllergies);
  }

  private boolean isConditionPresent(List<String> conditions, String keyword) {
    if (conditions == null || conditions.isEmpty()) return false;
    String target = keyword.toUpperCase();
    for (String c : conditions) {
      if (c != null && c.toUpperCase().contains(target)) {
        return true;
      }
    }
    return false;
  }

  private List<Food> getFoodsForMealType(List<Food> foods, MealType mealType) {
    List<Food> matching = new ArrayList<>();
    for (Food f : foods) {
      if (f.getSuitableMealTypes() != null && f.getSuitableMealTypes().contains(mealType)) {
        matching.add(f);
      } else if (f.getSuitableMealTypes() == null || f.getSuitableMealTypes().isEmpty()) {
        // Fallback category heuristic
        if (mealType == MealType.SNACK && isSnackCategory(f.getCategory())) {
          matching.add(f);
        } else if (mealType != MealType.SNACK && !isSnackCategory(f.getCategory())) {
          matching.add(f);
        }
      }
    }
    return matching;
  }

  private boolean isSnackCategory(FoodCategory cat) {
    if (cat == null) return false;
    return cat == FoodCategory.SNACK || cat == FoodCategory.DAIRY ||
           cat == FoodCategory.FRUIT_VEG || cat == FoodCategory.SALAD;
  }

  private Food pickBestCandidate(
      List<Food> pool,
      int targetCalories,
      double targetProteinG,
      double targetCarbsG,
      double targetFatG,
      List<Double> effectivePref,
      Set<UUID> recentFoodIds,
      Random random) {

    record ScoredFood(Food food, double score) {}
    List<ScoredFood> scoredList = new ArrayList<>();

    for (Food f : pool) {
      int calo = f.getCaloriesPerServing() != null ? f.getCaloriesPerServing() : 0;
      double caloDist = Math.pow((double) (calo - targetCalories) / Math.max(100, targetCalories), 2);

      double protein = f.getProteinPerServing() != null ? f.getProteinPerServing().doubleValue() : 0.0;
      double carbs = f.getCarbsPerServing() != null ? f.getCarbsPerServing().doubleValue() : 0.0;
      double fat = f.getFatPerServing() != null ? f.getFatPerServing().doubleValue() : 0.0;

      double macroDist = 0.0;
      if (targetProteinG > 0) {
        macroDist += 0.5 * Math.pow((protein - targetProteinG) / Math.max(10.0, targetProteinG), 2);
      }
      if (targetCarbsG > 0) {
        macroDist += 0.3 * Math.pow((carbs - targetCarbsG) / Math.max(20.0, targetCarbsG), 2);
      }
      if (targetFatG > 0) {
        macroDist += 0.2 * Math.pow((fat - targetFatG) / Math.max(10.0, targetFatG), 2);
      }

      double sim = vectorEngine.cosineSimilarity(effectivePref, f.getFeatureVector());
      double penalty = (recentFoodIds != null && recentFoodIds.contains(f.getId())) ? 5.0 : 0.0;

      double fitness = 0.5 * caloDist + 0.3 * macroDist - 1.2 * sim + penalty;
      scoredList.add(new ScoredFood(f, fitness));
    }

    scoredList.sort(Comparator.comparingDouble(ScoredFood::score));

    int topK = Math.min(5, scoredList.size());
    int chosenIndex = random.nextInt(topK);
    return scoredList.get(chosenIndex).food();
  }

  private MealPlanFood buildPlanFood(Food food, MealType mealType, BigDecimal multiplier) {
    MealPlanFood item = new MealPlanFood();
    item.setFood(food);
    item.setMealType(mealType);
    item.setServingName(food.getDefaultServingName() != null ? food.getDefaultServingName() : "1 khẩu phần");
    item.setServingMultiplier(multiplier);

    BigDecimal defaultGrams = food.getDefaultServingGrams() != null ? food.getDefaultServingGrams() : BigDecimal.valueOf(100.0);
    item.setServingGrams(defaultGrams.multiply(multiplier).setScale(1, RoundingMode.HALF_UP));

    int baseCalo = food.getCaloriesPerServing() != null ? food.getCaloriesPerServing() : 0;
    item.setCalories((int) Math.round(baseCalo * multiplier.doubleValue()));

    BigDecimal baseProtein = food.getProteinPerServing() != null ? food.getProteinPerServing() : BigDecimal.ZERO;
    item.setProteinG(baseProtein.multiply(multiplier).setScale(1, RoundingMode.HALF_UP));

    BigDecimal baseCarbs = food.getCarbsPerServing() != null ? food.getCarbsPerServing() : BigDecimal.ZERO;
    item.setCarbsG(baseCarbs.multiply(multiplier).setScale(1, RoundingMode.HALF_UP));

    BigDecimal baseFat = food.getFatPerServing() != null ? food.getFatPerServing() : BigDecimal.ZERO;
    item.setFatG(baseFat.multiply(multiplier).setScale(1, RoundingMode.HALF_UP));

    BigDecimal baseSugar = food.getSugarPerServing() != null ? food.getSugarPerServing() : BigDecimal.ZERO;
    item.setSugarG(baseSugar.multiply(multiplier).setScale(1, RoundingMode.HALF_UP));

    BigDecimal baseFiber = food.getFiberPerServing() != null ? food.getFiberPerServing() : BigDecimal.ZERO;
    item.setFiberG(baseFiber.multiply(multiplier).setScale(1, RoundingMode.HALF_UP));

    int basePrice = food.getReferencePriceVnd() != null ? food.getReferencePriceVnd() : 0;
    item.setEstimatedPriceVnd((int) Math.round(basePrice * multiplier.doubleValue()));

    return item;
  }

  private void refinePlan(
      MealPlan plan,
      int targetCalories,
      Map<MealType, Integer> mealTargets,
      Map<MealType, List<Food>> candidatePools,
      Integer maxBudgetVnd,
      Random random) {

    int currentCalories = plan.getItems().stream().mapToInt(MealPlanFood::getCalories).sum();
    double deltaPct = Math.abs((double) (currentCalories - targetCalories) / targetCalories);

    if (deltaPct <= 0.05) {
      return; // Already within 5% tolerance
    }

    // Try tuning multipliers across items
    for (int iter = 0; iter < 20; iter++) {
      currentCalories = plan.getItems().stream().mapToInt(MealPlanFood::getCalories).sum();
      deltaPct = (double) (currentCalories - targetCalories) / targetCalories;

      if (Math.abs(deltaPct) <= 0.05) {
        break;
      }

      // If total is too low, increase multiplier of lowest meal or swap with denser food
      if (deltaPct < -0.05) {
        MealPlanFood lowest = plan.getItems().stream()
            .min(Comparator.comparingInt(MealPlanFood::getCalories))
            .orElse(null);

        if (lowest != null) {
          int currentIdx = getMultiplierIndex(lowest.getServingMultiplier());
          if (currentIdx < MULTIPLIERS.length - 1) {
            lowest.setServingMultiplier(MULTIPLIERS[currentIdx + 1]);
            recalculateItem(lowest);
            continue;
          }
        }
      }

      // If total is too high, decrease multiplier of highest meal
      if (deltaPct > 0.05) {
        MealPlanFood highest = plan.getItems().stream()
            .max(Comparator.comparingInt(MealPlanFood::getCalories))
            .orElse(null);

        if (highest != null) {
          int currentIdx = getMultiplierIndex(highest.getServingMultiplier());
          if (currentIdx > 0) {
            highest.setServingMultiplier(MULTIPLIERS[currentIdx - 1]);
            recalculateItem(highest);
            continue;
          }
        }
      }

      // 1-Swap iteration: replace 1 item with candidate to move closer
      MealPlanFood targetItem = plan.getItems().get(random.nextInt(plan.getItems().size()));
      List<Food> pool = candidatePools.getOrDefault(targetItem.getMealType(), List.of());
      if (pool.size() > 1) {
        Set<UUID> otherFoodIds = new HashSet<>();
        for (MealPlanFood it : plan.getItems()) {
          if (it != targetItem && it.getFood() != null && it.getFood().getId() != null) {
            otherFoodIds.add(it.getFood().getId());
          }
        }
        List<Food> diverseCandidates = pool.stream()
            .filter(f -> !f.getId().equals(targetItem.getFood().getId()) && !otherFoodIds.contains(f.getId()))
            .toList();
        List<Food> searchPool = !diverseCandidates.isEmpty() ? diverseCandidates : pool;

        Food newFood = searchPool.get(random.nextInt(searchPool.size()));
        if (!newFood.getId().equals(targetItem.getFood().getId())) {
          targetItem.setFood(newFood);
          recalculateItem(targetItem);
        }
      }
    }
  }

  private void enforceBudgetConstraint(MealPlan plan, Map<MealType, List<Food>> candidatePools, Integer maxBudgetVnd) {
    if (maxBudgetVnd == null || maxBudgetVnd <= 0) {
      return;
    }

    int totalCost = plan.getItems().stream().mapToInt(MealPlanFood::getEstimatedPriceVnd).sum();
    if (totalCost <= maxBudgetVnd) {
      return;
    }

    // Pass 1: Reduce multipliers down to 1.0x, and if needed to 0.75x
    List<MealPlanFood> sortedByPrice = new ArrayList<>(plan.getItems());
    sortedByPrice.sort(Comparator.comparingInt(MealPlanFood::getEstimatedPriceVnd).reversed());

    for (MealPlanFood item : sortedByPrice) {
      if (totalCost <= maxBudgetVnd) break;
      if (item.getServingMultiplier().compareTo(BigDecimal.valueOf(1.0)) > 0) {
        int oldPrice = item.getEstimatedPriceVnd();
        item.setServingMultiplier(BigDecimal.valueOf(1.0));
        recalculateItem(item);
        totalCost = totalCost - oldPrice + item.getEstimatedPriceVnd();
      }
    }

    if (totalCost > maxBudgetVnd) {
      for (MealPlanFood item : sortedByPrice) {
        if (totalCost <= maxBudgetVnd) break;
        if (item.getServingMultiplier().compareTo(MULTIPLIERS[0]) > 0) {
          int oldPrice = item.getEstimatedPriceVnd();
          item.setServingMultiplier(MULTIPLIERS[0]); // 0.75x
          recalculateItem(item);
          totalCost = totalCost - oldPrice + item.getEstimatedPriceVnd();
        }
      }
    }

    // Pass 2: If still exceeding budget, try swapping expensive items for cheaper alternatives in the candidate pool
    if (totalCost > maxBudgetVnd) {
      sortedByPrice.sort(Comparator.comparingInt(MealPlanFood::getEstimatedPriceVnd).reversed());
      for (MealPlanFood item : sortedByPrice) {
        if (totalCost <= maxBudgetVnd) break;

        List<Food> pool = candidatePools.getOrDefault(item.getMealType(), List.of());
        Set<UUID> otherFoodIds = new HashSet<>();
        for (MealPlanFood it : plan.getItems()) {
          if (it != item && it.getFood() != null && it.getFood().getId() != null) {
            otherFoodIds.add(it.getFood().getId());
          }
        }
        List<Food> diversePool = pool.stream()
            .filter(f -> !f.getId().equals(item.getFood().getId()) && !otherFoodIds.contains(f.getId()))
            .toList();
        List<Food> searchPool = !diversePool.isEmpty() ? diversePool : pool;

        Optional<Food> cheaperOpt = searchPool.stream()
            .filter(f -> !f.getId().equals(item.getFood().getId()))
            .min(Comparator.comparingInt(f -> f.getReferencePriceVnd() != null ? f.getReferencePriceVnd() : 0));

        if (cheaperOpt.isPresent()) {
          Food replacement = cheaperOpt.get();
          int repPrice = replacement.getReferencePriceVnd() != null ? replacement.getReferencePriceVnd() : 0;
          int itemPrice = item.getFood().getReferencePriceVnd() != null ? item.getFood().getReferencePriceVnd() : 0;
          if (repPrice < itemPrice) {
            int oldPrice = item.getEstimatedPriceVnd();
            item.setFood(replacement);
            recalculateItem(item);
            totalCost = totalCost - oldPrice + item.getEstimatedPriceVnd();
          }
        }
      }
    }

    // Final safety check: if still exceeding budget, reject with clear error
    if (totalCost > maxBudgetVnd) {
      throw new BusinessException(ErrorCode.INSUFFICIENT_FOOD_DATA,
          "Không thể tạo thực đơn trong giới hạn ngân sách " + maxBudgetVnd + " VND");
    }
  }

  private void enforceHardSugarCeiling(MealPlan plan, Map<MealType, List<Food>> candidatePools, BigDecimal maxSugarG, Integer maxBudgetVnd) {
    BigDecimal currentSugar = plan.getItems().stream()
        .map(MealPlanFood::getSugarG)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    if (currentSugar.compareTo(maxSugarG) <= 0) {
      return;
    }

    // Pass 1: Reduce multipliers down to 0.75x for highest sugar items
    List<MealPlanFood> sortedBySugar = new ArrayList<>(plan.getItems());
    sortedBySugar.sort(Comparator.comparing(MealPlanFood::getSugarG).reversed());

    for (MealPlanFood item : sortedBySugar) {
      if (currentSugar.compareTo(maxSugarG) <= 0) break;
      if (item.getServingMultiplier().compareTo(MULTIPLIERS[0]) > 0) {
        BigDecimal oldSugar = item.getSugarG();
        item.setServingMultiplier(MULTIPLIERS[0]); // 0.75x
        recalculateItem(item);
        currentSugar = currentSugar.subtract(oldSugar).add(item.getSugarG());
      }
    }

    // Pass 2: If still exceeding maxSugarG, swap highest sugar items with lowest sugar food from candidate pool (without violating budget)
    if (currentSugar.compareTo(maxSugarG) > 0) {
      sortedBySugar.sort(Comparator.comparing(MealPlanFood::getSugarG).reversed());
      for (MealPlanFood item : sortedBySugar) {
        if (currentSugar.compareTo(maxSugarG) <= 0) break;

        List<Food> pool = candidatePools.getOrDefault(item.getMealType(), List.of());
        Set<UUID> otherFoodIds = new HashSet<>();
        for (MealPlanFood it : plan.getItems()) {
          if (it != item && it.getFood() != null && it.getFood().getId() != null) {
            otherFoodIds.add(it.getFood().getId());
          }
        }
        List<Food> diversePool = pool.stream()
            .filter(f -> !f.getId().equals(item.getFood().getId()) && !otherFoodIds.contains(f.getId()))
            .toList();
        List<Food> searchPool = !diversePool.isEmpty() ? diversePool : pool;

        Optional<Food> lowerSugarOpt = searchPool.stream()
            .filter(f -> !f.getId().equals(item.getFood().getId()))
            .filter(f -> {
              if (maxBudgetVnd == null || maxBudgetVnd <= 0) return true;
              int currentTotalCost = plan.getItems().stream().mapToInt(MealPlanFood::getEstimatedPriceVnd).sum();
              int oldP = item.getEstimatedPriceVnd();
              int baseP = f.getReferencePriceVnd() != null ? f.getReferencePriceVnd() : 0;
              int newP = (int) Math.round(baseP * item.getServingMultiplier().doubleValue());
              return currentTotalCost - oldP + newP <= maxBudgetVnd;
            })
            .min(Comparator.comparing(f -> f.getSugarPerServing() != null ? f.getSugarPerServing() : BigDecimal.ZERO));

        if (lowerSugarOpt.isPresent()) {
          Food replacement = lowerSugarOpt.get();
          BigDecimal replacementSugar = replacement.getSugarPerServing() != null ? replacement.getSugarPerServing() : BigDecimal.ZERO;
          BigDecimal itemBaseSugar = item.getFood().getSugarPerServing() != null ? item.getFood().getSugarPerServing() : BigDecimal.ZERO;
          if (replacementSugar.compareTo(itemBaseSugar) < 0) {
            BigDecimal oldSugar = item.getSugarG();
            item.setFood(replacement);
            recalculateItem(item);
            currentSugar = currentSugar.subtract(oldSugar).add(item.getSugarG());
          }
        }
      }
    }

    // Final safety check
    if (currentSugar.compareTo(maxSugarG) > 0) {
      throw new BusinessException(ErrorCode.INSUFFICIENT_FOOD_DATA,
          "Không thể tạo thực đơn thỏa mãn giới hạn đường tối đa " + maxSugarG + "g/ngày cho người tiểu đường");
    }
  }

  private void rebalanceCaloriesWithinConstraints(
      MealPlan plan,
      int targetCalories,
      Integer maxBudgetVnd,
      boolean isDiabetic) {

    int minCal = (int) Math.round(targetCalories * 0.95);
    int maxCal = (int) Math.round(targetCalories * 1.05);

    int currentCal = plan.getItems().stream().mapToInt(MealPlanFood::getCalories).sum();
    if (currentCal >= minCal && currentCal <= maxCal) {
      return;
    }

    // If currentCal < minCal: boost multipliers of lowest-sugar and lowest-cost items
    if (currentCal < minCal) {
      List<MealPlanFood> itemsToBoost = new ArrayList<>(plan.getItems());
      itemsToBoost.sort(Comparator.comparing((MealPlanFood item) -> item.getFood().getSugarPerServing() != null ? item.getFood().getSugarPerServing() : BigDecimal.ZERO)
          .thenComparingInt(item -> item.getFood().getReferencePriceVnd() != null ? item.getFood().getReferencePriceVnd() : 0));

      for (MealPlanFood item : itemsToBoost) {
        if (currentCal >= minCal) break;
        int idx = getMultiplierIndex(item.getServingMultiplier());
        while (idx < MULTIPLIERS.length - 1 && currentCal < minCal) {
          BigDecimal nextMult = MULTIPLIERS[idx + 1];

          // Check sugar invariant for diabetics
          BigDecimal baseSugar = item.getFood().getSugarPerServing() != null ? item.getFood().getSugarPerServing() : BigDecimal.ZERO;
          BigDecimal projectedNewItemSugar = baseSugar.multiply(nextMult).setScale(1, RoundingMode.HALF_UP);
          BigDecimal currentTotalSugar = plan.getItems().stream().map(MealPlanFood::getSugarG).reduce(BigDecimal.ZERO, BigDecimal::add);
          BigDecimal projectedTotalSugar = currentTotalSugar.subtract(item.getSugarG()).add(projectedNewItemSugar);

          if (isDiabetic && projectedTotalSugar.compareTo(BigDecimal.valueOf(25.0)) > 0) {
            break; // cannot boost this item further without violating sugar limit
          }

          // Check budget invariant
          int basePrice = item.getFood().getReferencePriceVnd() != null ? item.getFood().getReferencePriceVnd() : 0;
          int projectedNewItemPrice = (int) Math.round(basePrice * nextMult.doubleValue());
          int currentTotalCost = plan.getItems().stream().mapToInt(MealPlanFood::getEstimatedPriceVnd).sum();
          int projectedTotalCost = currentTotalCost - item.getEstimatedPriceVnd() + projectedNewItemPrice;

          if (maxBudgetVnd != null && maxBudgetVnd > 0 && projectedTotalCost > maxBudgetVnd) {
            break; // cannot boost due to budget limit
          }

          int oldCal = item.getCalories();
          item.setServingMultiplier(nextMult);
          recalculateItem(item);
          currentCal = currentCal - oldCal + item.getCalories();
          idx++;
        }
      }
    } else if (currentCal > maxCal) {
      // If currentCal > maxCal: reduce multipliers of highest calorie items
      List<MealPlanFood> itemsToReduce = new ArrayList<>(plan.getItems());
      itemsToReduce.sort(Comparator.comparingInt(MealPlanFood::getCalories).reversed());

      for (MealPlanFood item : itemsToReduce) {
        if (currentCal <= maxCal) break;
        int idx = getMultiplierIndex(item.getServingMultiplier());
        while (idx > 0 && currentCal > maxCal) {
          BigDecimal prevMult = MULTIPLIERS[idx - 1];
          int oldCal = item.getCalories();
          item.setServingMultiplier(prevMult);
          recalculateItem(item);
          currentCal = currentCal - oldCal + item.getCalories();
          idx--;
        }
      }
    }

    // Final joint invariant checks
    int finalCal = plan.getItems().stream().mapToInt(MealPlanFood::getCalories).sum();
    int finalCost = plan.getItems().stream().mapToInt(MealPlanFood::getEstimatedPriceVnd).sum();
    BigDecimal finalSugar = plan.getItems().stream().map(MealPlanFood::getSugarG).reduce(BigDecimal.ZERO, BigDecimal::add);

    if (finalCal < minCal || finalCal > maxCal) {
      throw new BusinessException(ErrorCode.INSUFFICIENT_FOOD_DATA,
          "Không thể tạo thực đơn thỏa mãn mục tiêu calo trong khoảng cho phép (" + minCal + " - " + maxCal + " kcal)");
    }
    if (maxBudgetVnd != null && maxBudgetVnd > 0 && finalCost > maxBudgetVnd) {
      throw new BusinessException(ErrorCode.INSUFFICIENT_FOOD_DATA,
          "Không thể tạo thực đơn trong giới hạn ngân sách " + maxBudgetVnd + " VND");
    }
    if (isDiabetic && finalSugar.compareTo(BigDecimal.valueOf(25.0)) > 0) {
      throw new BusinessException(ErrorCode.INSUFFICIENT_FOOD_DATA,
          "Không thể tạo thực đơn thỏa mãn giới hạn đường tối đa 25g/ngày cho người tiểu đường");
    }
  }

  private int getMultiplierIndex(BigDecimal mult) {
    if (mult == null) return 1;
    for (int i = 0; i < MULTIPLIERS.length; i++) {
      if (MULTIPLIERS[i].compareTo(mult) == 0) return i;
    }
    return 1;
  }

  private void recalculateItem(MealPlanFood item) {
    Food food = item.getFood();
    BigDecimal mult = item.getServingMultiplier();

    item.setServingName(food.getDefaultServingName() != null ? food.getDefaultServingName() : "1 khẩu phần");
    BigDecimal defaultGrams = food.getDefaultServingGrams() != null ? food.getDefaultServingGrams() : BigDecimal.valueOf(100.0);
    item.setServingGrams(defaultGrams.multiply(mult).setScale(1, RoundingMode.HALF_UP));

    int baseCalo = food.getCaloriesPerServing() != null ? food.getCaloriesPerServing() : 0;
    item.setCalories((int) Math.round(baseCalo * mult.doubleValue()));

    BigDecimal baseProtein = food.getProteinPerServing() != null ? food.getProteinPerServing() : BigDecimal.ZERO;
    item.setProteinG(baseProtein.multiply(mult).setScale(1, RoundingMode.HALF_UP));

    BigDecimal baseCarbs = food.getCarbsPerServing() != null ? food.getCarbsPerServing() : BigDecimal.ZERO;
    item.setCarbsG(baseCarbs.multiply(mult).setScale(1, RoundingMode.HALF_UP));

    BigDecimal baseFat = food.getFatPerServing() != null ? food.getFatPerServing() : BigDecimal.ZERO;
    item.setFatG(baseFat.multiply(mult).setScale(1, RoundingMode.HALF_UP));

    BigDecimal baseSugar = food.getSugarPerServing() != null ? food.getSugarPerServing() : BigDecimal.ZERO;
    item.setSugarG(baseSugar.multiply(mult).setScale(1, RoundingMode.HALF_UP));

    BigDecimal baseFiber = food.getFiberPerServing() != null ? food.getFiberPerServing() : BigDecimal.ZERO;
    item.setFiberG(baseFiber.multiply(mult).setScale(1, RoundingMode.HALF_UP));

    int basePrice = food.getReferencePriceVnd() != null ? food.getReferencePriceVnd() : 0;
    item.setEstimatedPriceVnd((int) Math.round(basePrice * mult.doubleValue()));
  }

  private void calculateTotals(MealPlan plan, List<Double> effectivePref) {
    int calo = 0;
    BigDecimal protein = BigDecimal.ZERO;
    BigDecimal carbs = BigDecimal.ZERO;
    BigDecimal fat = BigDecimal.ZERO;
    BigDecimal sugar = BigDecimal.ZERO;
    BigDecimal fiber = BigDecimal.ZERO;
    int cost = 0;
    double simSum = 0.0;

    for (MealPlanFood item : plan.getItems()) {
      calo += item.getCalories();
      protein = protein.add(item.getProteinG());
      carbs = carbs.add(item.getCarbsG());
      fat = fat.add(item.getFatG());
      sugar = sugar.add(item.getSugarG());
      fiber = fiber.add(item.getFiberG());
      cost += item.getEstimatedPriceVnd();

      if (item.getFood() != null) {
        simSum += vectorEngine.cosineSimilarity(effectivePref, item.getFood().getFeatureVector());
      }
    }

    plan.setTotalCalories(calo);
    plan.setTotalProteinG(protein);
    plan.setTotalCarbsG(carbs);
    plan.setTotalFatG(fat);
    plan.setTotalSugarG(sugar);
    plan.setTotalFiberG(fiber);
    plan.setEstimatedCostVnd(cost);

    double avgSim = plan.getItems().isEmpty() ? 0.0 : simSum / plan.getItems().size();
    plan.setPreferenceMatchScore(BigDecimal.valueOf(avgSim).setScale(2, RoundingMode.HALF_UP));
  }
}
