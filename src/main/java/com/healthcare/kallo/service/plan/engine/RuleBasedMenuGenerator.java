package com.healthcare.kallo.service.plan.engine;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.model.food.enums.FoodSource;
import com.healthcare.kallo.model.food.enums.MealType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/**
 * Deterministic-capable 3-phase rule-based + vector similarity meal plan generator. Executes wholly
 * in memory over preloaded candidate foods.
 */
@Component
public class RuleBasedMenuGenerator {

  private static final double WEIGHT_CALORIE = 0.5;
  private static final double WEIGHT_PROTEIN = 0.2;
  private static final double WEIGHT_PREF = 1.5;
  private static final double RECENT_PENALTY = 5.0;
  private static final double INTRA_DAY_PENALTY = 3.0;
  private static final double CALORIE_TOLERANCE_PCT = 0.05;
  private static final int BUDGET_FLEX_TOLERANCE_VND = 100_000;
  private static final BigDecimal DIABETES_MAX_ITEM_SUGAR_G = new BigDecimal("10.0");
  private static final BigDecimal DIABETES_MAX_DAILY_SUGAR_G = new BigDecimal("25.0");
  private static final int MAX_REFINEMENT_ITERS = 20;
  private static final int RECENT_DAYS_WINDOW = 3;
  private static final int SNACK_MAX_CALORIES = 320;
  private static final int SNACK_FALLBACK_MAX_CALORIES = 300;
  private static final int CONDIMENT_MAX_CALORIES = 60;
  private static final int VEG_SIDE_MIN_CALORIES = 35;
  private static final int SOUP_SIDE_MIN_CALORIES = 40;
  private static final int SOUP_SIDE_MAX_CALORIES = 210;
  private static final int COMPLETE_MAIN_MIN_CALORIES = 150;
  private static final int COMPLETE_SOUP_MIN_CALORIES = 200;
  private static final int TOP_K = 8;
  private static final int MAX_COMBO_OPTIONS = 1500;

  private static final List<BigDecimal> MULTIPLIERS =
      List.of(
          new BigDecimal("0.75"),
          new BigDecimal("1.00"),
          new BigDecimal("1.25"),
          new BigDecimal("1.50"));

  private final VectorSimilarityEngine vectorEngine;

  public RuleBasedMenuGenerator(VectorSimilarityEngine vectorEngine) {
    this.vectorEngine = vectorEngine;
  }

  public record GenerationInput(
      LocalDate startDate,
      int daysCount,
      int dailyCalorieGoal,
      double dailyProteinGoalG,
      Integer maxBudgetVnd,
      double[] rawPreferenceVector,
      Set<String> conditionCodes,
      List<Food> eligibleFoods,
      Set<UUID> recentFoodIds,
      Random random) {
    public GenerationInput {
      random = random != null ? random : ThreadLocalRandom.current();
      conditionCodes = conditionCodes != null ? conditionCodes : Set.of();
      recentFoodIds = recentFoodIds != null ? recentFoodIds : Set.of();
    }
  }

  public record GeneratedItem(
      Food food,
      MealType mealType,
      BigDecimal servingMultiplier,
      int calories,
      BigDecimal proteinG,
      BigDecimal carbsG,
      BigDecimal fatG,
      BigDecimal sugarG,
      BigDecimal fiberG,
      int estimatedPriceVnd,
      double cosineSimilarity) {}

  public record GeneratedDay(
      int dayIndex,
      LocalDate mealDate,
      int targetCalories,
      int totalCalories,
      int estimatedCostVnd,
      BigDecimal totalSugarG,
      List<GeneratedItem> items) {}

  public record GeneratedPlan(
      List<GeneratedDay> days,
      int totalCalories,
      BigDecimal totalProteinG,
      BigDecimal totalCarbsG,
      BigDecimal totalFatG,
      BigDecimal totalSugarG,
      BigDecimal totalFiberG,
      int estimatedCostVnd,
      BigDecimal preferenceMatchScore,
      String medicalWarning) {}

  public GeneratedPlan generate(GenerationInput input) {
    boolean hasDiabetes = vectorEngine.hasDiabetes(input.conditionCodes());
    double[] effectivePref =
        vectorEngine.applyClinicalMasks(input.rawPreferenceVector(), input.conditionCodes());

    List<Food> filtered = filterHardConstraints(input, hasDiabetes);
    if (filtered.isEmpty()) {
      throw new BusinessException(ErrorCode.INSUFFICIENT_FOOD_DATA);
    }

    Map<UUID, Double> cosineByFoodId = precomputeCosines(filtered, effectivePref);
    Map<MealType, List<Food>> slotCandidates = partitionByMealSlot(filtered, hasDiabetes);

    boolean includeSnack = !slotCandidates.get(MealType.SNACK).isEmpty();
    Map<MealType, Double> weights = mealWeights(includeSnack);
    for (MealType slot : weights.keySet()) {
      if (slotCandidates.get(slot).isEmpty()) {
        throw new BusinessException(ErrorCode.INSUFFICIENT_FOOD_DATA);
      }
    }

    Deque<Set<UUID>> rollingRecentDays = new ArrayDeque<>();
    if (!input.recentFoodIds().isEmpty()) {
      rollingRecentDays.addLast(Set.copyOf(input.recentFoodIds()));
    }

    List<GeneratedDay> generatedDays = new ArrayList<>(input.daysCount());
    for (int dayIndex = 1; dayIndex <= input.daysCount(); dayIndex++) {
      LocalDate mealDate = input.startDate().plusDays(dayIndex - 1L);
      Set<UUID> recentWindow = unionSets(rollingRecentDays);
      GeneratedDay day =
          generateSingleDay(
              dayIndex,
              mealDate,
              input,
              hasDiabetes,
              weights,
              slotCandidates,
              cosineByFoodId,
              recentWindow);
      generatedDays.add(day);

      Set<UUID> usedToday = new HashSet<>();
      for (GeneratedItem item : day.items()) {
        if (!isStarchBase(item.food())) {
          usedToday.add(item.food().getId());
        }
      }
      rollingRecentDays.addLast(usedToday);
      while (rollingRecentDays.size() > RECENT_DAYS_WINDOW) {
        rollingRecentDays.removeFirst();
      }
    }

    int sumCalories = 0;
    BigDecimal sumPro = BigDecimal.ZERO;
    BigDecimal sumCarbs = BigDecimal.ZERO;
    BigDecimal sumFat = BigDecimal.ZERO;
    BigDecimal sumSugar = BigDecimal.ZERO;
    BigDecimal sumFiber = BigDecimal.ZERO;
    int sumCost = 0;
    double cosineSum = 0.0;
    int itemCount = 0;

    for (GeneratedDay day : generatedDays) {
      sumCalories += day.totalCalories();
      sumCost += day.estimatedCostVnd();
      for (GeneratedItem item : day.items()) {
        sumPro = sumPro.add(item.proteinG());
        sumCarbs = sumCarbs.add(item.carbsG());
        sumFat = sumFat.add(item.fatG());
        sumSugar = sumSugar.add(item.sugarG());
        sumFiber = sumFiber.add(item.fiberG());
        cosineSum += item.cosineSimilarity();
        itemCount++;
      }
    }

    BigDecimal prefMatchScore =
        itemCount > 0
            ? BigDecimal.valueOf(cosineSum / itemCount).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

    return new GeneratedPlan(
        generatedDays,
        sumCalories,
        sumPro.setScale(1, RoundingMode.HALF_UP),
        sumCarbs.setScale(1, RoundingMode.HALF_UP),
        sumFat.setScale(1, RoundingMode.HALF_UP),
        sumSugar.setScale(1, RoundingMode.HALF_UP),
        sumFiber.setScale(1, RoundingMode.HALF_UP),
        sumCost,
        prefMatchScore,
        vectorEngine.buildMedicalWarning(input.conditionCodes()));
  }

  public GeneratedItem scaleItem(
      Food food, MealType mealType, BigDecimal multiplier, double cosineSimilarity) {
    BigDecimal normMult = multiplier.setScale(2, RoundingMode.HALF_UP);
    int calories =
        BigDecimal.valueOf(food.getCaloriesPerServing() != null ? food.getCaloriesPerServing() : 0)
            .multiply(normMult)
            .setScale(0, RoundingMode.HALF_UP)
            .intValue();
    BigDecimal protein = scaleMacro(food.getProteinPerServing(), normMult);
    BigDecimal carbs = scaleMacro(food.getCarbsPerServing(), normMult);
    BigDecimal fat = scaleMacro(food.getFatPerServing(), normMult);
    BigDecimal sugar = scaleMacro(food.getSugarPerServing(), normMult);
    BigDecimal fiber = scaleMacro(food.getFiberPerServing(), normMult);
    int price =
        food.getReferencePriceVnd() != null
            ? BigDecimal.valueOf(food.getReferencePriceVnd())
                .multiply(normMult)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue()
            : 0;
    return new GeneratedItem(
        food, mealType, normMult, calories, protein, carbs, fat, sugar, fiber, price, cosineSimilarity);
  }

  private GeneratedDay generateSingleDay(
      int dayIndex,
      LocalDate mealDate,
      GenerationInput input,
      boolean hasDiabetes,
      Map<MealType, Double> weights,
      Map<MealType, List<Food>> slotCandidates,
      Map<UUID, Double> cosineByFoodId,
      Set<UUID> recentWindow) {
    List<MutableSelection> selections = new ArrayList<>();
    Set<UUID> pickedToday = new HashSet<>();

    for (Map.Entry<MealType, Double> entry : weights.entrySet()) {
      MealType slot = entry.getKey();
      double cSlot = Math.max(50.0, input.dailyCalorieGoal() * entry.getValue());
      double pSlot =
          Math.min(input.dailyProteinGoalG() * entry.getValue(), (cSlot * 0.35) / 4.0);

      List<List<Food>> options =
          buildMealOptions(slot, slotCandidates.get(slot), cosineByFoodId, recentWindow);
      List<ScoredOption> scored = new ArrayList<>(options.size());
      for (List<Food> foods : options) {
        double fitness =
            scoreOption(
                foods,
                cSlot,
                pSlot,
                cosineByFoodId,
                recentWindow,
                pickedToday,
                input.maxBudgetVnd(),
                hasDiabetes);
        scored.add(new ScoredOption(foods, fitness));
      }
      scored.sort(Comparator.comparingDouble(ScoredOption::fitness));
      List<Food> chosen = selectWeightedTopK(scored, input.random());
      for (Food food : chosen) {
        if (!isStarchBase(food)) {
          pickedToday.add(food.getId());
        }
        selections.add(
            new MutableSelection(
                food,
                slot,
                BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP),
                cosineByFoodId.get(food.getId()),
                classifyRole(slot, food, chosen.size())));
      }
    }

    refineSelections(
        selections,
        input.dailyCalorieGoal(),
        input.maxBudgetVnd(),
        hasDiabetes,
        slotCandidates,
        cosineByFoodId,
        recentWindow);

    DayTotals totals = computeTotals(selections);
    if (!satisfiesAllHardConstraints(totals, input.dailyCalorieGoal(), input.maxBudgetVnd(), hasDiabetes)) {
      throw new BusinessException(ErrorCode.INSUFFICIENT_FOOD_DATA);
    }

    List<GeneratedItem> items =
        selections.stream()
            .map(s -> scaleItem(s.food, s.mealType, s.multiplier, s.cosine))
            .toList();
    return new GeneratedDay(
        dayIndex,
        mealDate,
        input.dailyCalorieGoal(),
        totals.calories,
        totals.cost,
        totals.sugar,
        items);
  }

  private void refineSelections(
      List<MutableSelection> selections,
      int targetCalories,
      Integer maxBudgetVnd,
      boolean hasDiabetes,
      Map<MealType, List<Food>> slotCandidates,
      Map<UUID, Double> cosineByFoodId,
      Set<UUID> recentWindow) {
    for (int iter = 0; iter < MAX_REFINEMENT_ITERS; iter++) {
      DayTotals current = computeTotals(selections);
      if (satisfiesAllHardConstraints(current, targetCalories, maxBudgetVnd, hasDiabetes)) {
        return;
      }
      double currentPenalty = constraintPenalty(current, targetCalories, maxBudgetVnd, hasDiabetes);

      // Step 3a: Try adjusting servingMultiplier in {0.75, 1.00, 1.25, 1.50}
      int bestIdx = -1;
      BigDecimal bestMult = null;
      double bestMultPenalty = currentPenalty;

      for (int i = 0; i < selections.size(); i++) {
        MutableSelection sel = selections.get(i);
        BigDecimal origMult = sel.multiplier;
        for (BigDecimal candidateMult : MULTIPLIERS) {
          if (candidateMult.compareTo(origMult) == 0) continue;
          sel.multiplier = candidateMult;
          DayTotals next = computeTotals(selections);
          double nextPenalty = constraintPenalty(next, targetCalories, maxBudgetVnd, hasDiabetes);
          if (nextPenalty + 1e-6 < bestMultPenalty) {
            bestMultPenalty = nextPenalty;
            bestIdx = i;
            bestMult = candidateMult;
          }
        }
        sel.multiplier = origMult;
      }

      if (bestIdx >= 0) {
        selections.get(bestIdx).multiplier = bestMult;
        continue;
      }

      // Step 3b: Try 1-Swap within same mealType and compatible dish role
      int swapIdx = -1;
      Food swapFood = null;
      BigDecimal swapMult = null;
      double bestSwapPenalty = currentPenalty;
      double bestSwapFitness = Double.MAX_VALUE;

      for (int i = 0; i < selections.size(); i++) {
        MutableSelection sel = selections.get(i);
        Food origFood = sel.food;
        BigDecimal origMult = sel.multiplier;
        double origCosine = sel.cosine;

        Set<UUID> sameSlotFoodIds = new HashSet<>();
        for (int j = 0; j < selections.size(); j++) {
          if (j != i && selections.get(j).mealType == sel.mealType) {
            sameSlotFoodIds.add(selections.get(j).food.getId());
          }
        }

        List<Food> pool = slotCandidates.getOrDefault(sel.mealType, List.of());
        for (Food candidate : pool) {
          if (candidate.getId().equals(origFood.getId())
              || sameSlotFoodIds.contains(candidate.getId())
              || !matchesRole(sel.role, candidate)) {
            continue;
          }
          double candCosine = cosineByFoodId.getOrDefault(candidate.getId(), 0.5);
          for (BigDecimal candMult : MULTIPLIERS) {
            sel.food = candidate;
            sel.multiplier = candMult;
            sel.cosine = candCosine;
            DayTotals next = computeTotals(selections);
            double nextPenalty = constraintPenalty(next, targetCalories, maxBudgetVnd, hasDiabetes);
            double tieBreaker =
                (recentWindow.contains(candidate.getId()) ? RECENT_PENALTY : 0.0) - candCosine;
            if (nextPenalty + 1e-6 < bestSwapPenalty
                || (Math.abs(nextPenalty - bestSwapPenalty) < 1e-6
                    && nextPenalty < currentPenalty
                    && tieBreaker < bestSwapFitness)) {
              bestSwapPenalty = nextPenalty;
              bestSwapFitness = tieBreaker;
              swapIdx = i;
              swapFood = candidate;
              swapMult = candMult;
            }
          }
        }
        sel.food = origFood;
        sel.multiplier = origMult;
        sel.cosine = origCosine;
      }

      if (swapIdx >= 0) {
        MutableSelection target = selections.get(swapIdx);
        target.food = swapFood;
        target.multiplier = swapMult;
        target.cosine = cosineByFoodId.getOrDefault(swapFood.getId(), 0.5);
      } else {
        break;
      }
    }
  }

  private List<Food> filterHardConstraints(GenerationInput input, boolean hasDiabetes) {
    Integer ceilingBudget =
        input.maxBudgetVnd() != null ? input.maxBudgetVnd() + BUDGET_FLEX_TOLERANCE_VND : null;
    List<Food> result = new ArrayList<>();
    for (Food food : input.eligibleFoods()) {
      if (food.getDeletedAt() != null || food.getSource() == FoodSource.OFF) {
        continue;
      }
      if (ceilingBudget != null
          && (food.getReferencePriceVnd() == null || food.getReferencePriceVnd() > ceilingBudget)) {
        continue;
      }
      if (hasDiabetes
          && food.getSugarPerServing() != null
          && food.getSugarPerServing().compareTo(DIABETES_MAX_ITEM_SUGAR_G) > 0) {
        continue;
      }
      result.add(food);
    }
    return result;
  }

  private Map<MealType, List<Food>> partitionByMealSlot(List<Food> foods, boolean hasDiabetes) {
    Map<MealType, List<Food>> map = new EnumMap<>(MealType.class);
    for (MealType type : MealType.values()) {
      map.put(type, new ArrayList<>());
    }
    for (Food food : foods) {
      List<String> tags = food.getSuitableMealTypes();
      if (tags == null || tags.isEmpty()) {
        map.get(MealType.BREAKFAST).add(food);
        map.get(MealType.LUNCH).add(food);
        map.get(MealType.DINNER).add(food);
        continue;
      }
      for (String tag : tags) {
        try {
          MealType mt = MealType.valueOf(tag);
          map.get(mt).add(food);
        } catch (IllegalArgumentException ignored) {
          // Ignore unrecognized meal tags cleanly
        }
      }
    }

    if (!map.get(MealType.SNACK).isEmpty()) {
      List<Food> lightSnacks =
          map.get(MealType.SNACK).stream()
              .filter(
                  f -> {
                    int cal = f.getCaloriesPerServing() != null ? f.getCaloriesPerServing() : 0;
                    return cal >= SOUP_SIDE_MIN_CALORIES && cal <= SNACK_MAX_CALORIES;
                  })
              .toList();
      if (!lightSnacks.isEmpty()) {
        map.put(MealType.SNACK, new ArrayList<>(lightSnacks));
      }
    } else {
      for (Food food : foods) {
        int cal = food.getCaloriesPerServing() != null ? food.getCaloriesPerServing() : 0;
        if (cal >= 15 && cal <= SNACK_FALLBACK_MAX_CALORIES) {
          if (!hasDiabetes
              || food.getSugarPerServing() == null
              || food.getSugarPerServing().compareTo(DIABETES_MAX_ITEM_SUGAR_G) <= 0) {
            map.get(MealType.SNACK).add(food);
          }
        }
      }
    }
    return map;
  }

  private Map<MealType, Double> mealWeights(boolean includeSnack) {
    Map<MealType, Double> map = new EnumMap<>(MealType.class);
    if (includeSnack) {
      map.put(MealType.BREAKFAST, 0.25);
      map.put(MealType.LUNCH, 0.35);
      map.put(MealType.DINNER, 0.30);
      map.put(MealType.SNACK, 0.10);
    } else {
      map.put(MealType.BREAKFAST, 25.0 / 90.0);
      map.put(MealType.LUNCH, 35.0 / 90.0);
      map.put(MealType.DINNER, 30.0 / 90.0);
    }
    return map;
  }

  private List<List<Food>> buildMealOptions(
      MealType slot,
      List<Food> candidates,
      Map<UUID, Double> cosineByFoodId,
      Set<UUID> recentWindow) {
    if (slot == MealType.SNACK) {
      List<List<Food>> singles = new ArrayList<>();
      for (Food f : candidates) {
        singles.add(List.of(f));
      }
      return singles;
    }

    List<Food> nonCondiments =
        candidates.stream()
            .filter(
                f ->
                    f.getCaloriesPerServing() != null
                        && f.getCaloriesPerServing() > CONDIMENT_MAX_CALORIES)
            .toList();
    if (nonCondiments.isEmpty()) {
      nonCondiments = candidates;
    }

    List<Food> completeMains = nonCondiments.stream().filter(this::isCompleteMain).toList();

    // Breakfast expects a single complete dish (Phở, Bún, Bánh cuốn, Bánh mì, Xôi...)
    if (slot == MealType.BREAKFAST) {
      if (!completeMains.isEmpty()) {
        List<List<Food>> breakfastSingles = new ArrayList<>(completeMains.size());
        for (Food complete : completeMains) {
          breakfastSingles.add(List.of(complete));
        }
        return breakfastSingles;
      }
      List<List<Food>> fallback = new ArrayList<>();
      for (Food f : nonCondiments) {
        fallback.add(List.of(f));
      }
      return fallback;
    }

    // Lunch & Dinner prefer 3-4 dish family-style combos: starch base + protein + vegetable (+ soup)
    List<Food> starchBases =
        prioritizeCandidates(
            candidates.stream().filter(this::isStarchBase).toList(), cosineByFoodId, recentWindow, 4);
    if (starchBases.isEmpty()) {
      starchBases =
          prioritizeCandidates(
              nonCondiments.stream()
                  .filter(
                      f ->
                          (f.getCategory() == FoodCategory.RICE
                                  || f.getCategory() == FoodCategory.STAPLE)
                              && !isProteinSide(f)
                              && (f.getCaloriesPerServing() == null
                                  || f.getCaloriesPerServing() <= 380))
                  .toList(),
              cosineByFoodId,
              recentWindow,
              4);
    }

    List<Food> proteins =
        prioritizeCandidates(
            candidates.stream().filter(this::isProteinSide).toList(),
            cosineByFoodId,
            recentWindow,
            18);
    List<Food> vegetables =
        prioritizeCandidates(
            candidates.stream().filter(this::isVegetableSide).toList(),
            cosineByFoodId,
            recentWindow,
            10);
    if (vegetables.isEmpty()) {
      vegetables =
          prioritizeCandidates(
              candidates.stream()
                  .filter(f -> f.getCategory() == FoodCategory.VEGETABLE)
                  .toList(),
              cosineByFoodId,
              recentWindow,
              6);
    }
    List<Food> soups =
        prioritizeCandidates(
            candidates.stream().filter(this::isSoupSide).toList(), cosineByFoodId, recentWindow, 6);

    List<List<Food>> multiDishOptions = new ArrayList<>();
    if (!starchBases.isEmpty() && !proteins.isEmpty()) {
      if (!vegetables.isEmpty() && !soups.isEmpty()) {
        for (Food base : starchBases) {
          for (Food protein : proteins) {
            for (Food veg : vegetables) {
              for (Food soup : soups) {
                if (distinctIds(base, protein, veg, soup)) {
                  multiDishOptions.add(List.of(base, protein, veg, soup));
                  if (multiDishOptions.size() >= MAX_COMBO_OPTIONS) {
                    break;
                  }
                }
              }
              if (multiDishOptions.size() >= MAX_COMBO_OPTIONS) break;
            }
            if (multiDishOptions.size() >= MAX_COMBO_OPTIONS) break;
          }
          if (multiDishOptions.size() >= MAX_COMBO_OPTIONS) break;
        }
      }

      if (!vegetables.isEmpty()) {
        for (Food base : starchBases) {
          for (Food protein : proteins) {
            for (Food veg : vegetables) {
              if (distinctIds(base, protein, veg) && multiDishOptions.size() < MAX_COMBO_OPTIONS) {
                multiDishOptions.add(List.of(base, protein, veg));
              }
            }
          }
        }
      }

      if (!soups.isEmpty()) {
        for (Food base : starchBases) {
          for (Food protein : proteins) {
            for (Food soup : soups) {
              if (distinctIds(base, protein, soup) && multiDishOptions.size() < MAX_COMBO_OPTIONS) {
                multiDishOptions.add(List.of(base, protein, soup));
              }
            }
          }
        }
      }
    }

    if (!multiDishOptions.isEmpty()) {
      return multiDishOptions;
    }

    // Fallback when pool is too small for 3-4 dishes: 2-dish pairs + complete mains
    List<List<Food>> options = new ArrayList<>();
    List<Food> fallbackBases =
        nonCondiments.stream()
            .filter(
                f ->
                    (f.getCategory() == FoodCategory.RICE || f.getCategory() == FoodCategory.STAPLE)
                        && !isProteinSide(f))
            .toList();
    List<Food> fallbackSides =
        candidates.stream()
            .filter(f -> isProteinSide(f) || isVegetableSide(f) || isSoupSide(f))
            .toList();
    for (Food base : fallbackBases) {
      for (Food side : fallbackSides) {
        if (!base.getId().equals(side.getId())) {
          options.add(List.of(base, side));
        }
      }
    }
    for (Food complete : completeMains) {
      options.add(List.of(complete));
    }
    if (options.isEmpty()) {
      for (Food f : nonCondiments) {
        options.add(List.of(f));
      }
    }
    return options;
  }

  private List<Food> prioritizeCandidates(
      List<Food> list, Map<UUID, Double> cosineByFoodId, Set<UUID> recentWindow, int limit) {
    if (list.size() <= limit) {
      return list;
    }
    return list.stream()
        .sorted(
            Comparator.comparingDouble(
                f ->
                    (recentWindow.contains(f.getId()) ? 1.0 : 0.0)
                        - cosineByFoodId.getOrDefault(f.getId(), 0.5)))
        .limit(limit)
        .toList();
  }

  private static boolean distinctIds(Food... foods) {
    Set<UUID> seen = new HashSet<>(foods.length * 2);
    for (Food f : foods) {
      if (!seen.add(f.getId())) {
        return false;
      }
    }
    return true;
  }

  private boolean isCompleteMain(Food food) {
    int cal = food.getCaloriesPerServing() != null ? food.getCaloriesPerServing() : 0;
    if (cal < COMPLETE_MAIN_MIN_CALORIES || isPureBaseOnly(food) || isProteinSide(food)) {
      return false;
    }
    FoodCategory cat = food.getCategory();
    if (cat == FoodCategory.SOUP) {
      return cal >= COMPLETE_SOUP_MIN_CALORIES;
    }
    return cat == FoodCategory.NOODLE
        || cat == FoodCategory.RICE
        || cat == FoodCategory.STAPLE;
  }

  private boolean isPureBaseOnly(Food food) {
    Integer cid = food.getVietfoodClassId();
    if (cid != null && (cid == 11 || cid == 25 || cid == 30 || cid == 47 || cid == 56)) {
      return true;
    }
    String name = normalizeName(food.getName());
    return name.equals("cơm")
        || name.equals("com")
        || name.equals("cơm trắng")
        || name.equals("com trang")
        || name.equals("cơm gạo lứt")
        || name.equals("com gao lut")
        || name.equals("bún")
        || name.equals("bun")
        || name.equals("bún tươi")
        || name.equals("bun tuoi")
        || name.equals("đậu hũ")
        || name.equals("dau hu")
        || name.equals("đậu phụ")
        || name.equals("dau phu")
        || name.equals("phô mai")
        || name.equals("pho mai")
        || name.equals("trứng")
        || name.equals("trung")
        || name.equals("trứng luộc")
        || name.equals("trung luoc");
  }

  private boolean isStarchBase(Food food) {
    Integer cid = food.getVietfoodClassId();
    if (cid != null && (cid == 11 || cid == 25)) {
      return true;
    }
    String name = normalizeName(food.getName());
    int cal = food.getCaloriesPerServing() != null ? food.getCaloriesPerServing() : 0;
    return ((name.equals("cơm")
            || name.equals("com")
            || name.equals("cơm trắng")
            || name.equals("com trang")
            || name.equals("cơm gạo lứt")
            || name.equals("com gao lut")
            || name.equals("bún")
            || name.equals("bun")
            || name.equals("bún tươi")
            || name.equals("bun tuoi"))
        && cal >= 100
        && cal <= 260);
  }

  private boolean isProteinSide(Food food) {
    if (food.getCategory() == FoodCategory.MEAT || food.getCategory() == FoodCategory.SEAFOOD) {
      return true;
    }
    Integer cid = food.getVietfoodClassId();
    if (cid != null && (cid == 30 || cid == 56)) {
      return true;
    }
    String name = normalizeName(food.getName());
    return name.contains("đậu hũ")
        || name.contains("dau hu")
        || name.contains("đậu phụ")
        || name.contains("dau phu")
        || name.equals("trứng")
        || name.equals("trung")
        || name.startsWith("trứng ")
        || name.startsWith("trung ");
  }

  private boolean isVegetableSide(Food food) {
    int cal = food.getCaloriesPerServing() != null ? food.getCaloriesPerServing() : 0;
    return food.getCategory() == FoodCategory.VEGETABLE && cal >= VEG_SIDE_MIN_CALORIES;
  }

  private boolean isSoupSide(Food food) {
    int cal = food.getCaloriesPerServing() != null ? food.getCaloriesPerServing() : 0;
    return food.getCategory() == FoodCategory.SOUP
        && cal >= SOUP_SIDE_MIN_CALORIES
        && cal <= SOUP_SIDE_MAX_CALORIES;
  }

  private static String normalizeName(String raw) {
    return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
  }

  private DishRole classifyRole(MealType slot, Food food, int mealItemCount) {
    if (slot == MealType.SNACK) {
      return DishRole.SNACK;
    }
    if (mealItemCount == 1) {
      return DishRole.COMPLETE_MAIN;
    }
    if (isStarchBase(food)
        || ((food.getCategory() == FoodCategory.RICE || food.getCategory() == FoodCategory.STAPLE)
            && !isProteinSide(food))) {
      return DishRole.STARCH_BASE;
    }
    if (isProteinSide(food)) {
      return DishRole.PROTEIN;
    }
    if (food.getCategory() == FoodCategory.VEGETABLE) {
      return DishRole.VEGETABLE;
    }
    if (food.getCategory() == FoodCategory.SOUP) {
      return DishRole.SOUP_SIDE;
    }
    return DishRole.COMPLETE_MAIN;
  }

  private boolean matchesRole(DishRole role, Food candidate) {
    return switch (role) {
      case SNACK -> candidate.getCaloriesPerServing() != null && candidate.getCaloriesPerServing() > 0;
      case STARCH_BASE -> isStarchBase(candidate)
          || ((candidate.getCategory() == FoodCategory.RICE
                  || candidate.getCategory() == FoodCategory.STAPLE)
              && !isProteinSide(candidate)
              && (candidate.getCaloriesPerServing() == null
                  || candidate.getCaloriesPerServing() <= 380));
      case PROTEIN -> isProteinSide(candidate);
      case VEGETABLE -> isVegetableSide(candidate)
          || candidate.getCategory() == FoodCategory.VEGETABLE;
      case SOUP_SIDE -> isSoupSide(candidate);
      case COMPLETE_MAIN -> isCompleteMain(candidate)
          || (candidate.getCaloriesPerServing() != null
              && candidate.getCaloriesPerServing() > CONDIMENT_MAX_CALORIES);
    };
  }

  private double scoreOption(
      List<Food> foods,
      double cSlot,
      double pSlot,
      Map<UUID, Double> cosineByFoodId,
      Set<UUID> recentWindow,
      Set<UUID> pickedToday,
      Integer maxBudgetVnd,
      boolean hasDiabetes) {
    double totalCal = 0.0;
    double totalPro = 0.0;
    double sumCos = 0.0;
    double penalty = 0.0;
    double sugar = 0.0;
    int price = 0;

    for (Food f : foods) {
      totalCal += f.getCaloriesPerServing() != null ? f.getCaloriesPerServing() : 0.0;
      totalPro += f.getProteinPerServing() != null ? f.getProteinPerServing().doubleValue() : 0.0;
      sugar += f.getSugarPerServing() != null ? f.getSugarPerServing().doubleValue() : 0.0;
      price += f.getReferencePriceVnd() != null ? f.getReferencePriceVnd() : 0;
      sumCos += cosineByFoodId.getOrDefault(f.getId(), 0.5);
      if (!isStarchBase(f)) {
        if (recentWindow.contains(f.getId())) {
          penalty += RECENT_PENALTY;
        }
        if (pickedToday.contains(f.getId())) {
          penalty += INTRA_DAY_PENALTY;
        }
      }
    }

    double avgCosine = sumCos / foods.size();
    double calErr = (totalCal - cSlot) / Math.max(cSlot, 1.0);
    double proErr = (totalPro - pSlot) / Math.max(pSlot, 1.0);

    if (hasDiabetes && sugar > DIABETES_MAX_DAILY_SUGAR_G.doubleValue() * 0.45) {
      penalty += 2.0;
    }
    if (maxBudgetVnd != null) {
      double softMealTarget = (maxBudgetVnd + BUDGET_FLEX_TOLERANCE_VND * 0.5) * 0.40;
      if (price > softMealTarget) {
        penalty += 0.35 * ((price - softMealTarget) / Math.max(softMealTarget, 1.0));
      }
    }

    return WEIGHT_CALORIE * (calErr * calErr)
        + WEIGHT_PROTEIN * (proErr * proErr)
        - WEIGHT_PREF * avgCosine
        + penalty;
  }

  private List<Food> selectWeightedTopK(List<ScoredOption> scored, Random random) {
    int k = Math.min(TOP_K, scored.size());
    double best = scored.get(0).fitness();
    double[] weights = new double[k];
    double sum = 0.0;
    for (int i = 0; i < k; i++) {
      double delta = Math.max(0.0, scored.get(i).fitness() - best);
      double w = Math.exp(-2.5 * delta);
      weights[i] = w;
      sum += w;
    }
    if (sum <= 0.0) {
      return scored.get(0).foods();
    }
    double r = random.nextDouble() * sum;
    double cumulative = 0.0;
    for (int i = 0; i < k; i++) {
      cumulative += weights[i];
      if (r <= cumulative) {
        return scored.get(i).foods();
      }
    }
    return scored.get(0).foods();
  }

  private Map<UUID, Double> precomputeCosines(List<Food> foods, double[] effectivePref) {
    Map<UUID, Double> map = new HashMap<>(foods.size() * 2);
    for (Food f : foods) {
      double[] fVec = vectorEngine.normalizeFoodVector(f.getFeatureVector());
      map.put(f.getId(), vectorEngine.cosineSimilarity(effectivePref, fVec));
    }
    return map;
  }

  private DayTotals computeTotals(List<MutableSelection> selections) {
    int cal = 0;
    int cost = 0;
    BigDecimal sugar = BigDecimal.ZERO;
    for (MutableSelection sel : selections) {
      GeneratedItem scaled = scaleItem(sel.food, sel.mealType, sel.multiplier, sel.cosine);
      cal += scaled.calories();
      cost += scaled.estimatedPriceVnd();
      sugar = sugar.add(scaled.sugarG());
    }
    return new DayTotals(cal, cost, sugar.setScale(1, RoundingMode.HALF_UP));
  }

  public boolean satisfiesAllHardConstraints(
      int calories,
      int cost,
      BigDecimal sugar,
      int targetCalories,
      Integer maxBudgetVnd,
      boolean hasDiabetes) {
    return satisfiesAllHardConstraints(
        new DayTotals(calories, cost, sugar), targetCalories, maxBudgetVnd, hasDiabetes);
  }

  private boolean satisfiesAllHardConstraints(
      DayTotals totals, int targetCalories, Integer maxBudgetVnd, boolean hasDiabetes) {
    double maxDiff = targetCalories * CALORIE_TOLERANCE_PCT;
    boolean calOk = Math.abs(totals.calories - targetCalories) <= maxDiff + 1e-6;
    boolean sugarOk = !hasDiabetes || totals.sugar.compareTo(DIABETES_MAX_DAILY_SUGAR_G) <= 0;
    boolean budgetOk =
        maxBudgetVnd == null || totals.cost <= maxBudgetVnd + BUDGET_FLEX_TOLERANCE_VND;
    return calOk && sugarOk && budgetOk;
  }

  private double constraintPenalty(
      DayTotals totals, int targetCalories, Integer maxBudgetVnd, boolean hasDiabetes) {
    double penalty = 0.0;
    if (maxBudgetVnd != null) {
      int ceiling = maxBudgetVnd + BUDGET_FLEX_TOLERANCE_VND;
      if (totals.cost > ceiling) {
        penalty += 1_000_000.0 + (totals.cost - ceiling);
      } else if (totals.cost > maxBudgetVnd) {
        penalty += (totals.cost - maxBudgetVnd) * 0.0001;
      }
    }
    if (hasDiabetes && totals.sugar.compareTo(DIABETES_MAX_DAILY_SUGAR_G) > 0) {
      double excess = totals.sugar.subtract(DIABETES_MAX_DAILY_SUGAR_G).doubleValue();
      penalty += 100_000.0 + excess * 100.0;
    }
    double maxDiff = targetCalories * CALORIE_TOLERANCE_PCT;
    double absDiff = Math.abs(totals.calories - targetCalories);
    if (absDiff > maxDiff) {
      penalty += 1_000.0 + (absDiff - maxDiff);
    } else {
      penalty += absDiff * 0.001;
    }
    return penalty;
  }

  private static BigDecimal scaleMacro(BigDecimal base, BigDecimal multiplier) {
    BigDecimal val = base != null ? base : BigDecimal.ZERO;
    return val.multiply(multiplier).setScale(1, RoundingMode.HALF_UP);
  }

  private static Set<UUID> unionSets(Deque<Set<UUID>> sets) {
    Set<UUID> union = new LinkedHashSet<>();
    for (Set<UUID> s : sets) {
      union.addAll(s);
    }
    return union;
  }

  private record ScoredOption(List<Food> foods, double fitness) {}

  private record DayTotals(int calories, int cost, BigDecimal sugar) {}

  private enum DishRole {
    COMPLETE_MAIN,
    STARCH_BASE,
    PROTEIN,
    VEGETABLE,
    SOUP_SIDE,
    SNACK
  }

  private static final class MutableSelection {
    Food food;
    final MealType mealType;
    BigDecimal multiplier;
    double cosine;
    final DishRole role;

    MutableSelection(
        Food food, MealType mealType, BigDecimal multiplier, double cosine, DishRole role) {
      this.food = food;
      this.mealType = mealType;
      this.multiplier = multiplier;
      this.cosine = cosine;
      this.role = role;
    }
  }
}
