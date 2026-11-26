package com.healthcare.kallo.service.plan.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.common.PageResponse;
import com.healthcare.kallo.dto.request.plan.GenerateMealPlanRequest;
import com.healthcare.kallo.dto.request.plan.SwapMealPlanItemRequest;
import com.healthcare.kallo.dto.response.plan.MealPlanDayResponse;
import com.healthcare.kallo.dto.response.plan.MealPlanItemResponse;
import com.healthcare.kallo.dto.response.plan.MealPlanResponse;
import com.healthcare.kallo.dto.response.plan.MealPlanSlotResponse;
import com.healthcare.kallo.dto.response.plan.MealPlanSummaryResponse;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.model.food.enums.FoodSource;
import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.model.plan.MealPlan;
import com.healthcare.kallo.model.plan.MealPlanFood;
import com.healthcare.kallo.model.plan.enums.MealPlanStatus;
import com.healthcare.kallo.model.plan.enums.MealPlanType;
import com.healthcare.kallo.model.profile.UserChronicCondition;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.model.profile.enums.Gender;
import com.healthcare.kallo.repository.food.FoodRepository;
import com.healthcare.kallo.repository.nutri.UserFoodLogRepository;
import com.healthcare.kallo.repository.plan.MealPlanRepository;
import com.healthcare.kallo.repository.profile.UserChronicConditionRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.act.UserTimeResolver;
import com.healthcare.kallo.service.food.FoodImageStorageService;
import com.healthcare.kallo.service.nutri.FoodLogService;
import com.healthcare.kallo.service.nutri.NutriAllergyChecker;
import com.healthcare.kallo.service.plan.MealPlanService;
import com.healthcare.kallo.service.plan.engine.RuleBasedMenuGenerator;
import com.healthcare.kallo.service.plan.engine.VectorSimilarityEngine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MealPlanServiceImpl implements MealPlanService {

  private static final UUID EMPTY_SENTINEL_UUID = new UUID(0L, 0L);
  private static final int MALE_MIN_SAFE_CALORIES = 1500;
  private static final int FEMALE_MIN_SAFE_CALORIES = 1200;
  private static final int MAX_LIST_PAGE_SIZE = 50;
  private static final BigDecimal DIABETES_MAX_ITEM_SUGAR_G = new BigDecimal("10.0");
  private static final BigDecimal DIABETES_MAX_DAILY_SUGAR_G = new BigDecimal("25.0");
  private static final DateTimeFormatter NAME_DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final List<BigDecimal> SWAP_MULTIPLIERS =
      List.of(
          new BigDecimal("0.75"),
          new BigDecimal("1.00"),
          new BigDecimal("1.25"),
          new BigDecimal("1.50"));

  private final MealPlanRepository mealPlanRepository;
  private final FoodRepository foodRepository;
  private final UserHealthProfileRepository profileRepository;
  private final UserChronicConditionRepository chronicConditionRepository;
  private final UserFoodLogRepository userFoodLogRepository;
  private final NutriAllergyChecker allergyChecker;
  private final FoodLogService foodLogService;
  private final RuleBasedMenuGenerator menuGenerator;
  private final VectorSimilarityEngine vectorEngine;
  private final UserTimeResolver userTimeResolver;
  private final FoodImageStorageService foodImageStorageService;
  private final Clock clock;

  public MealPlanServiceImpl(
      MealPlanRepository mealPlanRepository,
      FoodRepository foodRepository,
      UserHealthProfileRepository profileRepository,
      UserChronicConditionRepository chronicConditionRepository,
      UserFoodLogRepository userFoodLogRepository,
      NutriAllergyChecker allergyChecker,
      FoodLogService foodLogService,
      RuleBasedMenuGenerator menuGenerator,
      VectorSimilarityEngine vectorEngine,
      UserTimeResolver userTimeResolver,
      FoodImageStorageService foodImageStorageService,
      Clock clock) {
    this.mealPlanRepository = mealPlanRepository;
    this.foodRepository = foodRepository;
    this.profileRepository = profileRepository;
    this.chronicConditionRepository = chronicConditionRepository;
    this.userFoodLogRepository = userFoodLogRepository;
    this.allergyChecker = allergyChecker;
    this.foodLogService = foodLogService;
    this.menuGenerator = menuGenerator;
    this.vectorEngine = vectorEngine;
    this.userTimeResolver = userTimeResolver;
    this.foodImageStorageService = foodImageStorageService;
    this.clock = clock;
  }

  @Override
  @Transactional
  public MealPlanResponse generate(UUID userId, GenerateMealPlanRequest request) {
    userTimeResolver.zoneOf(userId);
    UserHealthProfile profile =
        profileRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND));

    int daysCount = request.days() != null ? request.days() : 1;
    if (daysCount < 1 || daysCount > 7) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }

    int dailyCalorieTarget = resolveCalorieTarget(profile, request.calorieTargetOverride());
    boolean includeCustomFoods =
        request.includeCustomFoods() == null || request.includeCustomFoods();
    Set<String> conditionCodes = loadConditionCodes(userId);

    Collection<UUID> excludedIds =
        request.excludedFoodIds() != null && !request.excludedFoodIds().isEmpty()
            ? request.excludedFoodIds()
            : List.of(EMPTY_SENTINEL_UUID);
    boolean hasExcludedIds =
        request.excludedFoodIds() != null && !request.excludedFoodIds().isEmpty();

    List<Food> eligibleFoods =
        foodRepository.findEligibleFoodsForMealPlan(
            userId,
            includeCustomFoods,
            hasExcludedIds,
            excludedIds,
            request.maxBudgetVnd() != null);

    Set<UUID> recentFoodIds =
        new HashSet<>(
            userFoodLogRepository.findLoggedFoodIdsSince(
                userId, request.planDate().minusDays(2)));

    double[] rawPref = vectorEngine.normalizePreference(profile.getPreferenceVector());
    RuleBasedMenuGenerator.GeneratedPlan generated =
        menuGenerator.generate(
            new RuleBasedMenuGenerator.GenerationInput(
                request.planDate(),
                daysCount,
                dailyCalorieTarget,
                profile.getProteinGrams().doubleValue(),
                request.maxBudgetVnd(),
                rawPref,
                conditionCodes,
                eligibleFoods,
                recentFoodIds,
                null));

    MealPlan plan = new MealPlan();
    plan.setUserId(userId);
    plan.setName(resolvePlanName(request.name(), request.planDate(), daysCount, conditionCodes));
    plan.setType(daysCount == 1 ? MealPlanType.DAILY : MealPlanType.WEEKLY);
    plan.setPlanDate(request.planDate());
    plan.setDaysCount(daysCount);
    plan.setStatus(MealPlanStatus.DRAFT);
    plan.setTargetCalories(dailyCalorieTarget);
    plan.setTotalCalories(generated.totalCalories());
    plan.setTotalProteinG(generated.totalProteinG());
    plan.setTotalCarbsG(generated.totalCarbsG());
    plan.setTotalFatG(generated.totalFatG());
    plan.setTotalSugarG(generated.totalSugarG());
    plan.setTotalFiberG(generated.totalFiberG());
    plan.setEstimatedCostVnd(generated.estimatedCostVnd());
    plan.setMaxBudgetVnd(request.maxBudgetVnd());
    plan.setPreferenceMatchScore(generated.preferenceMatchScore());
    plan.setMedicalWarning(generated.medicalWarning());

    Map<UUID, Food> foodById = new HashMap<>();
    for (RuleBasedMenuGenerator.GeneratedDay day : generated.days()) {
      for (RuleBasedMenuGenerator.GeneratedItem genItem : day.items()) {
        Food food = genItem.food();
        foodById.put(food.getId(), food);
        MealPlanFood entityItem = new MealPlanFood();
        entityItem.setMealPlan(plan);
        entityItem.setFoodId(food.getId());
        entityItem.setDayIndex(day.dayIndex());
        entityItem.setMealDate(day.mealDate());
        entityItem.setMealType(genItem.mealType());
        entityItem.setServingName(food.getDefaultServingName());
        entityItem.setServingGrams(food.getDefaultServingGrams());
        entityItem.setServingMultiplier(genItem.servingMultiplier());
        entityItem.setCalories(genItem.calories());
        entityItem.setProteinG(genItem.proteinG());
        entityItem.setCarbsG(genItem.carbsG());
        entityItem.setFatG(genItem.fatG());
        entityItem.setSugarG(genItem.sugarG());
        entityItem.setFiberG(genItem.fiberG());
        entityItem.setEstimatedPriceVnd(genItem.estimatedPriceVnd());
        plan.getItems().add(entityItem);
      }
    }

    MealPlan saved = mealPlanRepository.saveAndFlush(plan);
    return toResponse(saved, foodById);
  }

  @Override
  @Transactional
  public MealPlanResponse save(UUID userId, UUID mealPlanId) {
    MealPlan plan =
        mealPlanRepository
            .findWithItemsByIdAndUserId(mealPlanId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEAL_PLAN_NOT_FOUND));
    if (plan.getStatus() == MealPlanStatus.APPLIED) {
      throw new BusinessException(ErrorCode.MEAL_PLAN_ALREADY_APPLIED);
    }
    if (plan.getStatus() == MealPlanStatus.DRAFT) {
      plan.setStatus(MealPlanStatus.SAVED);
      plan = mealPlanRepository.saveAndFlush(plan);
    }
    return toResponse(plan, loadFoodsByIds(plan.getItems()));
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<MealPlanSummaryResponse> list(UUID userId, int page, int size) {
    int safePage = Math.max(0, page);
    int safeSize = Math.max(1, Math.min(size, MAX_LIST_PAGE_SIZE));
    Page<MealPlan> results =
        mealPlanRepository.findByUserIdOrderByPlanDateDescCreatedAtDesc(
            userId, PageRequest.of(safePage, safeSize));
    List<MealPlanSummaryResponse> items =
        results.getContent().stream().map(this::toSummaryResponse).toList();
    return new PageResponse<>(
        items, safePage, safeSize, results.getTotalElements(), results.getTotalPages());
  }

  @Override
  @Transactional(readOnly = true)
  public MealPlanResponse getById(UUID userId, UUID mealPlanId) {
    MealPlan plan =
        mealPlanRepository
            .findWithItemsByIdAndUserId(mealPlanId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEAL_PLAN_NOT_FOUND));
    return toResponse(plan, loadFoodsByIds(plan.getItems()));
  }

  @Override
  @Transactional
  public void delete(UUID userId, UUID mealPlanId) {
    MealPlan plan =
        mealPlanRepository
            .findByIdAndUserId(mealPlanId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEAL_PLAN_NOT_FOUND));
    mealPlanRepository.delete(plan);
  }

  @Override
  @Transactional
  public MealPlanResponse swapItem(
      UUID userId, UUID mealPlanId, UUID itemId, SwapMealPlanItemRequest request) {
    MealPlan plan =
        mealPlanRepository
            .findWithItemsByIdAndUserId(mealPlanId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEAL_PLAN_NOT_FOUND));
    if (plan.getStatus() == MealPlanStatus.APPLIED) {
      throw new BusinessException(ErrorCode.MEAL_PLAN_ALREADY_APPLIED);
    }

    MealPlanFood targetItem =
        plan.getItems().stream()
            .filter(i -> i.getId().equals(itemId))
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.MEAL_PLAN_ITEM_NOT_FOUND));

    UserHealthProfile profile =
        profileRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND));
    Set<String> conditionCodes = loadConditionCodes(userId);
    boolean hasDiabetes = vectorEngine.hasDiabetes(conditionCodes);

    Integer effectiveBudget =
        request != null && request.maxBudgetVndOverride() != null
            ? request.maxBudgetVndOverride()
            : plan.getMaxBudgetVnd();

    Set<UUID> excluded = new HashSet<>();
    excluded.add(targetItem.getFoodId());
    if (request != null && request.excludedFoodIds() != null) {
      excluded.addAll(request.excludedFoodIds());
    }

    List<Food> eligibleFoods =
        foodRepository.findEligibleFoodsForMealPlan(
            userId, true, true, excluded, effectiveBudget != null);
    if (eligibleFoods.isEmpty()) {
      throw new BusinessException(ErrorCode.NO_SUITABLE_SWAP_FOOD);
    }

    double[] effectivePref =
        vectorEngine.applyClinicalMasks(
            vectorEngine.normalizePreference(profile.getPreferenceVector()), conditionCodes);
    Set<UUID> recentFoodIds =
        new HashSet<>(
            userFoodLogRepository.findLoggedFoodIdsSince(
                userId, targetItem.getMealDate().minusDays(2)));

    int dayBaseCal = 0;
    int dayBaseCost = 0;
    BigDecimal dayBaseSugar = BigDecimal.ZERO;
    for (MealPlanFood item : plan.getItems()) {
      if (item.getDayIndex().equals(targetItem.getDayIndex()) && !item.getId().equals(itemId)) {
        dayBaseCal += item.getCalories();
        dayBaseCost += item.getEstimatedPriceVnd();
        dayBaseSugar = dayBaseSugar.add(item.getSugarG());
      }
    }

    int oldCal = targetItem.getCalories();
    double oldPro = targetItem.getProteinG().doubleValue();

    RuleBasedMenuGenerator.GeneratedItem bestSwap = null;
    double bestScore = Double.MAX_VALUE;

    for (Food candidate : eligibleFoods) {
      if (candidate.getSource() == FoodSource.OFF || candidate.getDeletedAt() != null) {
        continue;
      }
      if (!isSwapSlotEligible(candidate, targetItem.getMealType(), hasDiabetes)) {
        continue;
      }
      double cosine =
          vectorEngine.cosineSimilarity(
              effectivePref, vectorEngine.normalizeFoodVector(candidate.getFeatureVector()));

      for (BigDecimal multiplier : SWAP_MULTIPLIERS) {
        RuleBasedMenuGenerator.GeneratedItem scaled =
            menuGenerator.scaleItem(candidate, targetItem.getMealType(), multiplier, cosine);
        int dayCal = dayBaseCal + scaled.calories();
        int dayCost = dayBaseCost + scaled.estimatedPriceVnd();
        BigDecimal daySugar = dayBaseSugar.add(scaled.sugarG());

        if (!menuGenerator.satisfiesAllHardConstraints(
            dayCal, dayCost, daySugar, plan.getTargetCalories(), effectiveBudget, hasDiabetes)) {
          continue;
        }

        double deltaCal = Math.abs(scaled.calories() - oldCal);
        double deltaPro = Math.abs(scaled.proteinG().doubleValue() - oldPro);
        double recentPenalty = recentFoodIds.contains(candidate.getId()) ? 5.0 : 0.0;
        double score = deltaCal + 2.0 * deltaPro - 30.0 * cosine + recentPenalty;
        if (score < bestScore) {
          bestScore = score;
          bestSwap = scaled;
        }
      }
    }

    if (bestSwap == null) {
      throw new BusinessException(ErrorCode.NO_SUITABLE_SWAP_FOOD);
    }

    Food newFood = bestSwap.food();
    targetItem.setFoodId(newFood.getId());
    targetItem.setServingName(newFood.getDefaultServingName());
    targetItem.setServingGrams(newFood.getDefaultServingGrams());
    targetItem.setServingMultiplier(bestSwap.servingMultiplier());
    targetItem.setCalories(bestSwap.calories());
    targetItem.setProteinG(bestSwap.proteinG());
    targetItem.setCarbsG(bestSwap.carbsG());
    targetItem.setFatG(bestSwap.fatG());
    targetItem.setSugarG(bestSwap.sugarG());
    targetItem.setFiberG(bestSwap.fiberG());
    targetItem.setEstimatedPriceVnd(bestSwap.estimatedPriceVnd());

    Map<UUID, Food> foodById = loadFoodsByIds(plan.getItems());
    recomputePlanAggregates(plan, foodById, effectivePref, conditionCodes);
    MealPlan saved = mealPlanRepository.saveAndFlush(plan);
    return toResponse(saved, foodById);
  }

  @Override
  @Transactional
  public MealPlanResponse apply(UUID userId, UUID mealPlanId, LocalDate date, Integer dayIndex) {
    userTimeResolver.zoneOf(userId);
    MealPlan plan =
        mealPlanRepository
            .findWithItemsByIdAndUserId(mealPlanId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEAL_PLAN_NOT_FOUND));

    if (plan.getStatus() == MealPlanStatus.APPLIED) {
      throw new BusinessException(ErrorCode.MEAL_PLAN_ALREADY_APPLIED);
    }
    if (plan.getStatus() != MealPlanStatus.SAVED) {
      throw new BusinessException(ErrorCode.MEAL_PLAN_NOT_SAVED);
    }
    if (dayIndex != null && (dayIndex < 1 || dayIndex > plan.getDaysCount())) {
      throw new BusinessException(ErrorCode.INVALID_PARAMETER);
    }

    List<MealPlanFood> itemsToApply =
        dayIndex != null
            ? plan.getItems().stream().filter(i -> i.getDayIndex().equals(dayIndex)).toList()
            : plan.getItems();
    if (itemsToApply.isEmpty()) {
      throw new BusinessException(ErrorCode.MEAL_PLAN_ITEM_NOT_FOUND);
    }

    LocalDate baseDate = date != null ? date : plan.getPlanDate();
    Set<UUID> allergicIds = allergyChecker.loadUserAllergicIngredientIds(userId);
    Set<String> conditionCodes = loadConditionCodes(userId);
    boolean hasDiabetes = vectorEngine.hasDiabetes(conditionCodes);

    Map<LocalDate, BigDecimal> sugarByDate = new HashMap<>();
    Set<DateMealKey> checkedSlots = new LinkedHashSet<>();
    Instant now = Instant.now(clock);
    List<FoodLogService.MealPlanApplyItem> applyItems = new ArrayList<>(itemsToApply.size());

    for (MealPlanFood item : itemsToApply) {
      Food food =
          foodRepository
              .findByIdWithIngredients(item.getFoodId())
              .orElseThrow(() -> new BusinessException(ErrorCode.FOOD_ITEM_DELETED));
      if (food.getOwnerUserId() != null && !food.getOwnerUserId().equals(userId)) {
        throw new BusinessException(ErrorCode.FORBIDDEN_FOOD_ACCESS);
      }
      if (!allergyChecker.checkFoodAllergies(food, allergicIds).isEmpty()) {
        throw new BusinessException(ErrorCode.INSUFFICIENT_FOOD_DATA);
      }
      if (hasDiabetes && item.getSugarG().compareTo(DIABETES_MAX_ITEM_SUGAR_G) > 0) {
        throw new BusinessException(ErrorCode.INSUFFICIENT_FOOD_DATA);
      }

      LocalDate targetMealDate =
          dayIndex != null ? baseDate : baseDate.plusDays(item.getDayIndex() - 1L);
      BigDecimal daySugar = sugarByDate.getOrDefault(targetMealDate, BigDecimal.ZERO).add(item.getSugarG());
      if (hasDiabetes && daySugar.compareTo(DIABETES_MAX_DAILY_SUGAR_G) > 0) {
        throw new BusinessException(ErrorCode.INSUFFICIENT_FOOD_DATA);
      }
      sugarByDate.put(targetMealDate, daySugar);

      DateMealKey slotKey = new DateMealKey(targetMealDate, item.getMealType());
      if (checkedSlots.add(slotKey)
          && userFoodLogRepository.existsByUserIdAndLoggedDateAndMealTypeAndDeletedAtIsNull(
              userId, targetMealDate, item.getMealType())) {
        throw new BusinessException(ErrorCode.MEAL_LOG_ALREADY_EXISTS);
      }

      applyItems.add(
          new FoodLogService.MealPlanApplyItem(
              item.getFoodId(),
              item.getMealType(),
              item.getServingGrams(),
              item.getServingMultiplier(),
              targetMealDate,
              now,
              UUID.randomUUID()));
    }

    foodLogService.applyMealPlanItems(userId, applyItems);

    plan.setStatus(MealPlanStatus.APPLIED);
    plan.setAppliedAt(now);
    plan.setAppliedDate(baseDate);
    MealPlan saved = mealPlanRepository.saveAndFlush(plan);
    return toResponse(saved, loadFoodsByIds(saved.getItems()));
  }

  private int resolveCalorieTarget(UserHealthProfile profile, Integer override) {
    if (override == null) {
      return profile.getDailyCalorieGoal().setScale(0, RoundingMode.HALF_UP).intValue();
    }
    int floor =
        profile.getGender() == Gender.FEMALE
            ? FEMALE_MIN_SAFE_CALORIES
            : MALE_MIN_SAFE_CALORIES;
    if (override < floor) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
    return override;
  }

  private Set<String> loadConditionCodes(UUID userId) {
    List<UserChronicCondition> rows = chronicConditionRepository.findAllByUserIdWithCondition(userId);
    Set<String> codes = new LinkedHashSet<>();
    for (UserChronicCondition ucc : rows) {
      if (ucc.getCondition() != null && ucc.getCondition().isActive()) {
        codes.add(ucc.getCondition().getCode().trim().toUpperCase(Locale.ROOT));
      }
    }
    return codes;
  }

  private boolean isSwapSlotEligible(Food candidate, MealType mealType, boolean hasDiabetes) {
    if (hasDiabetes
        && candidate.getSugarPerServing() != null
        && candidate.getSugarPerServing().compareTo(DIABETES_MAX_ITEM_SUGAR_G) > 0) {
      return false;
    }
    List<String> tags = candidate.getSuitableMealTypes();
    if (mealType == MealType.SNACK) {
      boolean taggedSnack = tags != null && tags.contains(MealType.SNACK.name());
      int cal = candidate.getCaloriesPerServing() != null ? candidate.getCaloriesPerServing() : 0;
      return taggedSnack || (cal >= 15 && cal <= 300);
    }
    boolean suitableForSlot = tags == null || tags.isEmpty() || tags.contains(mealType.name());
    if (!suitableForSlot) {
      return false;
    }
    int cal = candidate.getCaloriesPerServing() != null ? candidate.getCaloriesPerServing() : 0;
    return cal > 60 || candidate.getCategory() != FoodCategory.VEGETABLE;
  }

  private void recomputePlanAggregates(
      MealPlan plan, Map<UUID, Food> foodById, double[] effectivePref, Set<String> conditionCodes) {
    int totalCal = 0;
    int totalCost = 0;
    BigDecimal pro = BigDecimal.ZERO;
    BigDecimal carbs = BigDecimal.ZERO;
    BigDecimal fat = BigDecimal.ZERO;
    BigDecimal sugar = BigDecimal.ZERO;
    BigDecimal fiber = BigDecimal.ZERO;
    double cosineSum = 0.0;

    for (MealPlanFood item : plan.getItems()) {
      totalCal += item.getCalories();
      totalCost += item.getEstimatedPriceVnd();
      pro = pro.add(item.getProteinG());
      carbs = carbs.add(item.getCarbsG());
      fat = fat.add(item.getFatG());
      sugar = sugar.add(item.getSugarG());
      fiber = fiber.add(item.getFiberG());
      Food f = foodById.get(item.getFoodId());
      double cos =
          f != null
              ? vectorEngine.cosineSimilarity(
                  effectivePref, vectorEngine.normalizeFoodVector(f.getFeatureVector()))
              : VectorSimilarityEngine.NEUTRAL_SIMILARITY;
      cosineSum += cos;
    }

    int count = plan.getItems().size();
    plan.setTotalCalories(totalCal);
    plan.setTotalProteinG(pro.setScale(1, RoundingMode.HALF_UP));
    plan.setTotalCarbsG(carbs.setScale(1, RoundingMode.HALF_UP));
    plan.setTotalFatG(fat.setScale(1, RoundingMode.HALF_UP));
    plan.setTotalSugarG(sugar.setScale(1, RoundingMode.HALF_UP));
    plan.setTotalFiberG(fiber.setScale(1, RoundingMode.HALF_UP));
    plan.setEstimatedCostVnd(totalCost);
    plan.setPreferenceMatchScore(
        count > 0
            ? BigDecimal.valueOf(cosineSum / count).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO);
    plan.setMedicalWarning(vectorEngine.buildMedicalWarning(conditionCodes));
  }

  private Map<UUID, Food> loadFoodsByIds(List<MealPlanFood> items) {
    List<UUID> foodIds = items.stream().map(MealPlanFood::getFoodId).distinct().toList();
    if (foodIds.isEmpty()) {
      return Collections.emptyMap();
    }
    return foodRepository.findAllById(foodIds).stream()
        .collect(Collectors.toMap(Food::getId, Function.identity()));
  }

  private MealPlanSummaryResponse toSummaryResponse(MealPlan plan) {
    return new MealPlanSummaryResponse(
        plan.getId(),
        plan.getName(),
        plan.getType(),
        plan.getPlanDate(),
        plan.getDaysCount(),
        plan.getStatus(),
        plan.getTargetCalories(),
        plan.getTotalCalories(),
        plan.getEstimatedCostVnd(),
        plan.getMaxBudgetVnd(),
        plan.getPreferenceMatchScore(),
        plan.getMedicalWarning(),
        plan.getCreatedAt());
  }

  private MealPlanResponse toResponse(MealPlan plan, Map<UUID, Food> foodById) {
    Map<Integer, List<MealPlanFood>> byDay = new TreeMap<>();
    for (MealPlanFood item : plan.getItems()) {
      byDay.computeIfAbsent(item.getDayIndex(), k -> new ArrayList<>()).add(item);
    }

    List<MealPlanDayResponse> days = new ArrayList<>(byDay.size());
    for (Map.Entry<Integer, List<MealPlanFood>> entry : byDay.entrySet()) {
      int dayIdx = entry.getKey();
      List<MealPlanFood> dayItems = entry.getValue();
      LocalDate mealDate = dayItems.get(0).getMealDate();
      boolean hasSnack = dayItems.stream().anyMatch(i -> i.getMealType() == MealType.SNACK);

      Map<MealType, List<MealPlanItemResponse>> bySlot = new EnumMap<>(MealType.class);
      for (MealPlanFood item : dayItems) {
        Food food = foodById.get(item.getFoodId());
        bySlot
            .computeIfAbsent(item.getMealType(), k -> new ArrayList<>())
            .add(toItemResponse(item, food));
      }

      List<MealPlanSlotResponse> meals = new ArrayList<>();
      int dayCal = 0;
      int dayCost = 0;
      for (MealPlanFood item : dayItems) {
        dayCal += item.getCalories();
        dayCost += item.getEstimatedPriceVnd();
      }
      for (MealType type : List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK)) {
        List<MealPlanItemResponse> slotItems = bySlot.get(type);
        if (slotItems == null || slotItems.isEmpty()) continue;
        int slotCal = slotItems.stream().mapToInt(MealPlanItemResponse::calories).sum();
        int slotTarget =
            (int) Math.round(plan.getTargetCalories() * slotWeight(type, hasSnack));
        meals.add(new MealPlanSlotResponse(type, slotTarget, slotCal, slotItems));
      }

      BigDecimal diffPct =
          BigDecimal.valueOf(dayCal - plan.getTargetCalories())
              .multiply(BigDecimal.valueOf(100))
              .divide(BigDecimal.valueOf(plan.getTargetCalories()), 2, RoundingMode.HALF_UP);

      days.add(
          new MealPlanDayResponse(
              dayIdx, mealDate, plan.getTargetCalories(), dayCal, diffPct, dayCost, meals));
    }

    return new MealPlanResponse(
        plan.getId(),
        plan.getName(),
        plan.getDescription(),
        plan.getType(),
        plan.getPlanDate(),
        plan.getDaysCount(),
        plan.getStatus(),
        plan.getTargetCalories(),
        plan.getTotalCalories(),
        plan.getTotalProteinG(),
        plan.getTotalCarbsG(),
        plan.getTotalFatG(),
        plan.getTotalSugarG(),
        plan.getTotalFiberG(),
        plan.getEstimatedCostVnd(),
        plan.getMaxBudgetVnd(),
        plan.getPreferenceMatchScore(),
        plan.getMedicalWarning(),
        plan.getAppliedAt(),
        plan.getAppliedDate(),
        days,
        plan.getCreatedAt(),
        plan.getUpdatedAt());
  }

  private MealPlanItemResponse toItemResponse(MealPlanFood item, Food food) {
    return new MealPlanItemResponse(
        item.getId(),
        item.getFoodId(),
        food != null ? food.getName() : null,
        food != null ? foodImageStorageService.resolveImageUrl(food.getImageUrl()) : null,
        food != null ? food.getSource() : null,
        item.getServingName(),
        item.getServingGrams(),
        item.getServingMultiplier(),
        item.getCalories(),
        item.getProteinG(),
        item.getCarbsG(),
        item.getFatG(),
        item.getSugarG(),
        item.getFiberG(),
        item.getEstimatedPriceVnd());
  }

  private static double slotWeight(MealType type, boolean hasSnack) {
    if (hasSnack) {
      return switch (type) {
        case BREAKFAST -> 0.25;
        case LUNCH -> 0.35;
        case DINNER -> 0.30;
        case SNACK -> 0.10;
      };
    }
    return switch (type) {
      case BREAKFAST -> 25.0 / 90.0;
      case LUNCH -> 35.0 / 90.0;
      case DINNER -> 30.0 / 90.0;
      case SNACK -> 0.0;
    };
  }

  private static String resolvePlanName(
      String requestedName, LocalDate planDate, int daysCount, Set<String> conditionCodes) {
    if (requestedName != null && !requestedName.isBlank()) {
      return requestedName.trim();
    }
    String dateLabel = NAME_DATE_FMT.format(planDate);
    String prefix =
        daysCount > 1 ? "Thực đơn " + daysCount + " ngày từ " : "Thực đơn ngày ";
    if (conditionCodes.stream().anyMatch(c -> c.startsWith("DIABETES"))) {
      return "Thực đơn kiểm soát đường huyết " + dateLabel;
    }
    return prefix + dateLabel;
  }

  private record DateMealKey(LocalDate date, MealType mealType) {}
}
