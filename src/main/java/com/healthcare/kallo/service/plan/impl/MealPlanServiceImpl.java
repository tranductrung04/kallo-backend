package com.healthcare.kallo.service.plan.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.request.plan.GenerateMealPlanRequest;
import com.healthcare.kallo.dto.request.plan.SaveMealPlanRequest;
import com.healthcare.kallo.dto.request.plan.SwapFoodItemRequest;
import com.healthcare.kallo.dto.response.nutri.FoodLogResponse;
import com.healthcare.kallo.dto.response.plan.*;
import com.healthcare.kallo.dto.response.report.DailySummaryResponse;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.model.nutri.UserFoodLog;
import com.healthcare.kallo.model.nutri.enums.FoodLogSource;
import com.healthcare.kallo.model.plan.MealPlan;
import com.healthcare.kallo.model.plan.MealPlanFood;
import com.healthcare.kallo.model.plan.enums.MealPlanStatus;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.model.profile.enums.Gender;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.food.FoodRepository;
import com.healthcare.kallo.repository.nutri.UserFoodLogRepository;
import com.healthcare.kallo.repository.plan.MealPlanFoodRepository;
import com.healthcare.kallo.repository.plan.MealPlanRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.act.UserTimeResolver;
import com.healthcare.kallo.service.food.AllergenNormalizer;
import com.healthcare.kallo.service.plan.MealPlanService;
import com.healthcare.kallo.service.plan.engine.RuleBasedMenuGenerator;
import com.healthcare.kallo.service.plan.engine.VectorSimilarityEngine;
import com.healthcare.kallo.service.report.DailySummaryService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MealPlanServiceImpl implements MealPlanService {

  private final MealPlanRepository mealPlanRepository;
  private final MealPlanFoodRepository mealPlanFoodRepository;
  private final FoodRepository foodRepository;
  private final UserHealthProfileRepository userHealthProfileRepository;
  private final UserRepository userRepository;
  private final UserFoodLogRepository userFoodLogRepository;
  private final DailySummaryService dailySummaryService;
  private final RuleBasedMenuGenerator menuGenerator;
  private final VectorSimilarityEngine vectorEngine;
  private final AllergenNormalizer allergenNormalizer;
  private final UserTimeResolver userTimeResolver;
  private final Clock clock;

  public MealPlanServiceImpl(
      MealPlanRepository mealPlanRepository,
      MealPlanFoodRepository mealPlanFoodRepository,
      FoodRepository foodRepository,
      UserHealthProfileRepository userHealthProfileRepository,
      UserRepository userRepository,
      UserFoodLogRepository userFoodLogRepository,
      DailySummaryService dailySummaryService,
      RuleBasedMenuGenerator menuGenerator,
      VectorSimilarityEngine vectorEngine,
      AllergenNormalizer allergenNormalizer,
      UserTimeResolver userTimeResolver,
      Clock clock) {
    this.mealPlanRepository = mealPlanRepository;
    this.mealPlanFoodRepository = mealPlanFoodRepository;
    this.foodRepository = foodRepository;
    this.userHealthProfileRepository = userHealthProfileRepository;
    this.userRepository = userRepository;
    this.userFoodLogRepository = userFoodLogRepository;
    this.dailySummaryService = dailySummaryService;
    this.menuGenerator = menuGenerator;
    this.vectorEngine = vectorEngine;
    this.allergenNormalizer = allergenNormalizer;
    this.userTimeResolver = userTimeResolver;
    this.clock = clock;
  }

  public MealPlanServiceImpl(
      MealPlanRepository mealPlanRepository,
      MealPlanFoodRepository mealPlanFoodRepository,
      FoodRepository foodRepository,
      UserHealthProfileRepository userHealthProfileRepository,
      UserRepository userRepository,
      UserFoodLogRepository userFoodLogRepository,
      DailySummaryService dailySummaryService,
      RuleBasedMenuGenerator menuGenerator,
      VectorSimilarityEngine vectorEngine) {
    this(
        mealPlanRepository,
        mealPlanFoodRepository,
        foodRepository,
        userHealthProfileRepository,
        userRepository,
        userFoodLogRepository,
        dailySummaryService,
        menuGenerator,
        vectorEngine,
        new AllergenNormalizer(),
        new UserTimeResolver(userRepository),
        Clock.systemUTC()
    );
  }

  @Override
  @Transactional
  public MealPlanResponse generateMealPlan(UUID userId, GenerateMealPlanRequest request) {
    if (request != null && request.days() != null && request.days() > 1) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR,
          "Hệ thống hiện tại chỉ hỗ trợ tạo thực đơn 1 ngày (days = 1). Tính năng tạo thực đơn nhiều ngày đang được phát triển.");
    }

    User user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));

    UserHealthProfile profile = userHealthProfileRepository.findByUserId(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND, "Health profile required to generate meal plan"));

    int minSafeThreshold = 1350;
    if (profile.getGender() == Gender.FEMALE) {
      minSafeThreshold = 1200;
    } else if (profile.getGender() == Gender.MALE) {
      minSafeThreshold = 1500;
    }

    if (request != null && request.calorieTargetOverride() != null) {
      if (request.calorieTargetOverride() < minSafeThreshold) {
        throw new BusinessException(ErrorCode.VALIDATION_ERROR,
            "Mục tiêu calo ghi đè (" + request.calorieTargetOverride() + " kcal) không được thấp hơn ngưỡng tối thiểu an toàn y tế ("
                + minSafeThreshold + " kcal) cho giới tính " + profile.getGender());
      }
    }

    int targetCalories = (request != null && request.calorieTargetOverride() != null)
        ? request.calorieTargetOverride()
        : profile.getDailyCalorieGoal();

    LocalDate planDate;
    if (request != null && request.planDate() != null) {
      planDate = request.planDate();
    } else {
      ZoneId userZone = userTimeResolver.zoneOf(userId);
      planDate = LocalDate.now(clock.withZone(userZone));
    }

    List<Food> allFoods = foodRepository.findAvailableFoodsForUser(userId);
    if (request != null && request.excludedFoodIds() != null && !request.excludedFoodIds().isEmpty()) {
      Set<UUID> excluded = new HashSet<>(request.excludedFoodIds());
      allFoods = allFoods.stream().filter(f -> !excluded.contains(f.getId())).toList();
    }

    List<Food> recentFoods = userFoodLogRepository.findRecentFoods(userId, PageRequest.of(0, 10));
    Set<UUID> recentIds = recentFoods.stream().map(Food::getId).collect(Collectors.toSet());

    RuleBasedMenuGenerator.PlanGenerationInput input = new RuleBasedMenuGenerator.PlanGenerationInput(
        targetCalories,
        profile.getProteinGoalG(),
        profile.getCarbsGoalG(),
        profile.getFatGoalG(),
        profile.getAllergies(),
        profile.getChronicConditions(),
        profile.getPreferenceVector(),
        request != null ? request.maxBudgetVnd() : null,
        recentIds,
        allFoods,
        planDate,
        null
    );

    MealPlan plan = menuGenerator.generate(input);
    plan.setUser(user);

    MealPlan saved = mealPlanRepository.save(plan);
    return toResponse(saved);
  }

  @Override
  @Transactional
  public MealPlanResponse saveMealPlan(UUID userId, UUID planId, SaveMealPlanRequest request) {
    MealPlan plan = mealPlanRepository.findByIdAndUserId(planId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.MEAL_PLAN_NOT_FOUND, "Meal plan not found"));

    if (request != null) {
      if (request.name() != null && !request.name().isBlank()) {
        plan.setName(request.name().trim());
      }
      if (request.description() != null) {
        plan.setDescription(request.description().trim());
      }
    }

    if (plan.getStatus() == MealPlanStatus.DRAFT) {
      plan.setStatus(MealPlanStatus.SAVED);
    }

    MealPlan saved = mealPlanRepository.save(plan);
    return toResponse(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<MealPlanResponse> getMealPlans(UUID userId, MealPlanStatus status, Pageable pageable) {
    Page<MealPlan> page;
    if (status != null) {
      page = mealPlanRepository.findByUserIdAndStatusOrderByPlanDateDesc(userId, status, pageable);
    } else {
      page = mealPlanRepository.findByUserIdOrderByPlanDateDesc(userId, pageable);
    }
    return page.map(this::toResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public MealPlanResponse getMealPlan(UUID userId, UUID planId) {
    MealPlan plan = mealPlanRepository.findByIdAndUserId(planId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.MEAL_PLAN_NOT_FOUND, "Meal plan not found"));
    return toResponse(plan);
  }

  @Override
  @Transactional
  public void deleteMealPlan(UUID userId, UUID planId) {
    MealPlan plan = mealPlanRepository.findByIdAndUserId(planId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.MEAL_PLAN_NOT_FOUND, "Meal plan not found"));

    if (plan.getStatus() == MealPlanStatus.APPLIED) {
      throw new BusinessException(ErrorCode.MEAL_PLAN_ALREADY_APPLIED, "Không thể xóa thực đơn đã được áp dụng");
    }

    mealPlanRepository.delete(plan);
  }

  @Override
  @Transactional
  public MealPlanResponse swapItem(UUID userId, UUID planId, UUID itemId, SwapFoodItemRequest request) {
    MealPlan plan = mealPlanRepository.findByIdAndUserId(planId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.MEAL_PLAN_NOT_FOUND, "Meal plan not found"));

    if (plan.getStatus() == MealPlanStatus.APPLIED) {
      throw new BusinessException(ErrorCode.MEAL_PLAN_ALREADY_APPLIED, "Không thể thay đổi món trong thực đơn đã được áp dụng");
    }

    MealPlanFood targetItem = plan.getItems().stream()
        .filter(item -> item.getId().equals(itemId))
        .findFirst()
        .orElseThrow(() -> new BusinessException(ErrorCode.MEAL_PLAN_ITEM_NOT_FOUND, "Meal plan item not found"));

    UserHealthProfile profile = userHealthProfileRepository.findByUserId(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND, "Health profile required"));

    boolean isDiabetic = profile.getChronicConditions() != null && profile.getChronicConditions().stream()
        .anyMatch(c -> c != null && (c.toUpperCase().contains("DIABETES") || c.toUpperCase().contains("TIEU_DUONG")));

    BigDecimal otherItemsSugar = plan.getItems().stream()
        .filter(item -> !item.getId().equals(itemId))
        .map(MealPlanFood::getSugarG)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    Food newFood;
    if (request != null && request.alternativeFoodId() != null) {
      newFood = foodRepository.findVisibleFoodById(request.alternativeFoodId(), userId)
          .orElseThrow(() -> new BusinessException(ErrorCode.FOOD_NOT_FOUND, "Alternative food not found"));

      if (newFood.getCategory() == FoodCategory.NON_FOOD) {
        throw new BusinessException(ErrorCode.INSUFFICIENT_FOOD_DATA, "Không thể chọn mục không phải thực phẩm (NON_FOOD) vào thực đơn");
      }

      if (!isSuitableForMealType(newFood, targetItem.getMealType())) {
        throw new BusinessException(ErrorCode.INSUFFICIENT_FOOD_DATA, "Món ăn thay thế không phù hợp với bữa " + targetItem.getMealType());
      }

      Set<String> userAllergies = allergenNormalizer.normalizeUserAllergies(profile.getAllergies());
      if (!isSafeFromAllergies(newFood, userAllergies)) {
        throw new BusinessException(ErrorCode.INSUFFICIENT_FOOD_DATA, "Món ăn thay thế xung đột với dị ứng của người dùng");
      }

      if (!isSafeFromChronicConditions(newFood, profile.getChronicConditions())) {
        throw new BusinessException(ErrorCode.INSUFFICIENT_FOOD_DATA, "Món ăn thay thế không phù hợp với điều kiện bệnh lý của người dùng");
      }

      if (isDiabetic) {
        BigDecimal baseSugar = newFood.getSugarPerServing() != null ? newFood.getSugarPerServing() : BigDecimal.ZERO;
        BigDecimal newSugar = baseSugar.multiply(targetItem.getServingMultiplier()).setScale(1, RoundingMode.HALF_UP);
        BigDecimal projectedTotalSugar = otherItemsSugar.add(newSugar);
        if (projectedTotalSugar.compareTo(BigDecimal.valueOf(25.0)) > 0) {
          throw new BusinessException(ErrorCode.INSUFFICIENT_FOOD_DATA,
              "Món ăn thay thế làm tổng lượng đường trong ngày vượt ngưỡng an toàn 25g cho người tiểu đường (dự kiến: " + projectedTotalSugar + "g)");
        }
      }
    } else {
      // Find suitable replacement food with sugar budget
      BigDecimal remainingSugarBudget = isDiabetic
          ? BigDecimal.valueOf(25.0).subtract(otherItemsSugar)
          : BigDecimal.valueOf(9999.0);

      List<Food> available = foodRepository.findAvailableFoodsForUser(userId);
      newFood = findBestSwapFood(available, targetItem, plan, profile, remainingSugarBudget);
    }

    targetItem.setFood(newFood);
    recalculateItem(targetItem);
    recalculatePlanTotals(plan, profile);

    // Enforce +/- 5% calorie target tolerance
    int targetCal = plan.getTargetCalories();
    if (targetCal > 0) {
      int minCal = (int) Math.round(targetCal * 0.95);
      int maxCal = (int) Math.round(targetCal * 1.05);

      if (plan.getTotalCalories() < minCal || plan.getTotalCalories() > maxCal) {
        BigDecimal[] candidateMultipliers = new BigDecimal[] {
            BigDecimal.valueOf(1.0),
            BigDecimal.valueOf(0.75),
            BigDecimal.valueOf(1.25),
            BigDecimal.valueOf(1.50)
        };
        boolean adjusted = false;
        BigDecimal originalMultiplier = targetItem.getServingMultiplier();

        for (BigDecimal candidateMult : candidateMultipliers) {
          if (candidateMult.compareTo(originalMultiplier) == 0) continue;
          targetItem.setServingMultiplier(candidateMult);
          recalculateItem(targetItem);
          recalculatePlanTotals(plan, profile);

          boolean sugarOk = !isDiabetic || plan.getTotalSugarG().compareTo(BigDecimal.valueOf(25.0)) <= 0;
          boolean budgetOk = plan.getMaxBudgetVnd() == null || plan.getMaxBudgetVnd() <= 0 || plan.getEstimatedCostVnd() <= plan.getMaxBudgetVnd();
          boolean caloOk = plan.getTotalCalories() >= minCal && plan.getTotalCalories() <= maxCal;

          if (caloOk && sugarOk && budgetOk) {
            adjusted = true;
            break;
          }
        }

        if (!adjusted) {
          targetItem.setServingMultiplier(originalMultiplier);
          recalculateItem(targetItem);
          recalculatePlanTotals(plan, profile);
          throw new BusinessException(ErrorCode.INSUFFICIENT_FOOD_DATA,
              "Món thay thế khiến tổng calo của thực đơn (" + plan.getTotalCalories() + " kcal) lệch quá 5% so với mục tiêu (" + targetCal + " kcal)");
        }
      }
    }

    if (plan.getMaxBudgetVnd() != null && plan.getMaxBudgetVnd() > 0 && plan.getEstimatedCostVnd() > plan.getMaxBudgetVnd()) {
      throw new BusinessException(ErrorCode.INSUFFICIENT_FOOD_DATA,
          "Món thay thế khiến chi phí thực đơn (" + plan.getEstimatedCostVnd() + " VND) vượt ngân sách tối đa (" + plan.getMaxBudgetVnd() + " VND)");
    }

    if (isDiabetic && plan.getTotalSugarG().compareTo(BigDecimal.valueOf(25.0)) > 0) {
      throw new BusinessException(ErrorCode.INSUFFICIENT_FOOD_DATA,
          "Tổng lượng đường trong ngày sau khi đổi món (" + plan.getTotalSugarG() + "g) vượt ngưỡng an toàn 25g cho người tiểu đường");
    }

    MealPlan saved = mealPlanRepository.save(plan);
    return toResponse(saved);
  }

  @Override
  @Transactional
  public ApplyMealPlanResponse applyMealPlan(UUID userId, UUID planId, LocalDate date) {
    MealPlan plan = mealPlanRepository.findByIdAndUserId(planId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.MEAL_PLAN_NOT_FOUND, "Meal plan not found"));

    if (plan.getStatus() == MealPlanStatus.DRAFT) {
      throw new BusinessException(ErrorCode.MEAL_PLAN_NOT_SAVED, "Meal plan must be saved before applying");
    }

    if (plan.getStatus() == MealPlanStatus.APPLIED) {
      throw new BusinessException(ErrorCode.MEAL_PLAN_ALREADY_APPLIED, "Meal plan has already been applied");
    }

    ZoneId userZone = userTimeResolver.zoneOf(userId);
    LocalDate targetDate;
    if (date != null) {
      targetDate = date;
    } else if (plan.getPlanDate() != null) {
      targetDate = plan.getPlanDate();
    } else {
      targetDate = LocalDate.now(clock.withZone(userZone));
    }

    // Re-validate plan against current user health profile before applying
    UserHealthProfile profile = userHealthProfileRepository.findByUserId(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND, "Health profile required to apply meal plan"));

    Set<String> normalizedAllergies = allergenNormalizer.normalizeUserAllergies(profile.getAllergies());
    boolean isDiabetic = profile.getChronicConditions() != null && profile.getChronicConditions().stream()
        .anyMatch(c -> c != null && (c.toUpperCase().contains("DIABETES") || c.toUpperCase().contains("TIEU_DUONG")));

    for (MealPlanFood item : plan.getItems()) {
      Food f = item.getFood();
      if (!isSafeFromAllergies(f, normalizedAllergies)) {
        throw new BusinessException(ErrorCode.INSUFFICIENT_FOOD_DATA,
            "Thực đơn này không còn an toàn với hồ sơ sức khỏe hiện tại của bạn: món '" + f.getName() + "' xung đột với dị ứng mới cập nhật.");
      }
      if (!isSafeFromChronicConditions(f, profile.getChronicConditions())) {
        throw new BusinessException(ErrorCode.INSUFFICIENT_FOOD_DATA,
            "Thực đơn này không còn an toàn với hồ sơ sức khỏe hiện tại của bạn: món '" + f.getName() + "' không phù hợp với bệnh lý của bạn.");
      }
    }

    if (isDiabetic && plan.getTotalSugarG() != null && plan.getTotalSugarG().compareTo(BigDecimal.valueOf(25.0)) > 0) {
      throw new BusinessException(ErrorCode.INSUFFICIENT_FOOD_DATA,
          "Thực đơn này vượt quá trần đường 25g/ngày dành cho người tiểu đường. Vui lòng tạo thực đơn mới hoặc đổi món trước khi áp dụng.");
    }

    // Reject if any meal type in the plan already exists in diary for targetDate to prevent duplicate logs
    for (MealPlanFood item : plan.getItems()) {
      if (userFoodLogRepository.existsByUserIdAndLoggedDateAndMealType(userId, targetDate, item.getMealType())) {
        throw new BusinessException(ErrorCode.MEAL_LOG_ALREADY_EXISTS,
            "Nhật ký ngày " + targetDate + " đã có món ăn cho bữa " + item.getMealType() + ". Không thể áp dụng thực đơn để tránh trùng lặp dữ liệu.");
      }
    }

    Instant now = Instant.now(clock);

    int updatedRows = mealPlanRepository.markPlanAppliedAtomically(planId, userId, now, targetDate);
    if (updatedRows == 0) {
      throw new BusinessException(ErrorCode.MEAL_PLAN_ALREADY_APPLIED, "Meal plan has already been applied");
    }

    plan.setStatus(MealPlanStatus.APPLIED);
    plan.setAppliedAt(now);
    plan.setAppliedDate(targetDate);

    User user = plan.getUser();

    List<UserFoodLog> logsToCreate = new ArrayList<>();
    for (MealPlanFood item : plan.getItems()) {
      UserFoodLog log = new UserFoodLog();
      log.setUser(user);
      log.setFood(item.getFood());
      log.setMealType(item.getMealType());
      log.setServingName(item.getServingName());
      log.setServingGrams(item.getServingGrams());
      log.setServingQuantity(item.getServingMultiplier());
      log.setSource(FoodLogSource.MEAL_PLAN);
      log.setLoggedDate(targetDate);

      LocalTime mealTime = switch (item.getMealType()) {
        case BREAKFAST -> LocalTime.of(7, 30);
        case LUNCH -> LocalTime.of(12, 0);
        case SNACK -> LocalTime.of(15, 30);
        case DINNER -> LocalTime.of(19, 0);
      };
      Instant itemLoggedAt = targetDate.atTime(mealTime).atZone(userZone).toInstant();
      log.setLoggedAt(itemLoggedAt);

      UUID clientUuid = UUID.nameUUIDFromBytes(
          (plan.getId().toString() + "_" + item.getId().toString() + "_" + targetDate.toString())
              .getBytes(StandardCharsets.UTF_8));
      log.setClientUuid(clientUuid);

      // Snapshot nutrition
      log.setCalories(BigDecimal.valueOf(item.getCalories()));
      log.setProteinG(item.getProteinG());
      log.setCarbsG(item.getCarbsG());
      log.setFatG(item.getFatG());
      log.setSugarG(item.getSugarG());
      log.setFiberG(item.getFiberG());

      logsToCreate.add(log);
    }

    userFoodLogRepository.saveAll(logsToCreate);

    // Recalculate daily summary
    dailySummaryService.recalculate(userId, targetDate);
    DailySummaryResponse summary = dailySummaryService.getSummary(userId, targetDate);

    int totalCaloriesLogged = logsToCreate.stream()
        .map(UserFoodLog::getCalories)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .intValue();

    return new ApplyMealPlanResponse(
        plan.getId(),
        targetDate,
        logsToCreate.size(),
        totalCaloriesLogged,
        summary
    );
  }

  private Food findBestSwapFood(List<Food> foods, MealPlanFood targetItem, MealPlan plan, UserHealthProfile profile, BigDecimal remainingSugarBudget) {
    Set<String> normalizedAllergies = allergenNormalizer.normalizeUserAllergies(profile.getAllergies());

    Set<UUID> otherFoodIds = plan.getItems().stream()
        .filter(it -> it != targetItem && it.getFood() != null && it.getFood().getId() != null)
        .map(it -> it.getFood().getId())
        .collect(Collectors.toSet());

    int otherCost = plan.getItems().stream()
        .filter(it -> it != targetItem)
        .mapToInt(MealPlanFood::getEstimatedPriceVnd)
        .sum();

    int otherCalories = plan.getItems().stream()
        .filter(it -> it != targetItem)
        .mapToInt(MealPlanFood::getCalories)
        .sum();
    int idealCalo = plan.getTargetCalories() > 0 ? plan.getTargetCalories() - otherCalories : targetItem.getCalories();

    List<Food> candidates = foods.stream()
        .filter(f -> f.getCategory() != FoodCategory.NON_FOOD)
        .filter(f -> !f.getId().equals(targetItem.getFood().getId()))
        .filter(f -> isSafeFromAllergies(f, normalizedAllergies))
        .filter(f -> isSuitableForMealType(f, targetItem.getMealType()))
        .filter(f -> isSafeFromChronicConditions(f, profile.getChronicConditions()))
        .filter(f -> {
          if (remainingSugarBudget == null) return true;
          BigDecimal sugarPerServing = f.getSugarPerServing() != null ? f.getSugarPerServing() : BigDecimal.ZERO;
          BigDecimal itemSugar = sugarPerServing.multiply(targetItem.getServingMultiplier()).setScale(1, RoundingMode.HALF_UP);
          return itemSugar.compareTo(remainingSugarBudget) <= 0;
        })
        .filter(f -> {
          if (plan.getMaxBudgetVnd() == null || plan.getMaxBudgetVnd() <= 0) return true;
          int basePrice = f.getReferencePriceVnd() != null ? f.getReferencePriceVnd() : 0;
          int itemPrice = (int) Math.round(basePrice * targetItem.getServingMultiplier().doubleValue());
          return otherCost + itemPrice <= plan.getMaxBudgetVnd();
        })
        .toList();

    if (candidates.isEmpty()) {
      throw new BusinessException(ErrorCode.NO_SUITABLE_SWAP_FOOD, "No suitable alternative food found for swap");
    }

    // Prioritize foods not yet used today to maintain intra-day diversity
    List<Food> diverseCandidates = candidates.stream()
        .filter(f -> !otherFoodIds.contains(f.getId()))
        .toList();
    List<Food> effectivePool = !diverseCandidates.isEmpty() ? diverseCandidates : candidates;

    BigDecimal currentProtein = targetItem.getProteinG();

    return effectivePool.stream()
        .min(Comparator.comparingDouble(f -> {
          int calo = f.getCaloriesPerServing() != null ? f.getCaloriesPerServing() : 0;
          int itemCalo = (int) Math.round(calo * targetItem.getServingMultiplier().doubleValue());
          BigDecimal protein = f.getProteinPerServing() != null ? f.getProteinPerServing() : BigDecimal.ZERO;

          double deltaCalo = Math.abs(itemCalo - idealCalo);
          double deltaProtein = Math.abs(protein.doubleValue() - currentProtein.doubleValue());
          return deltaCalo + 2.0 * deltaProtein;
        }))
        .orElse(effectivePool.get(0));
  }

  private boolean isSafeFromChronicConditions(Food food, List<String> conditions) {
    if (conditions == null || conditions.isEmpty()) return true;
    boolean isDiabetic = conditions.stream()
        .anyMatch(c -> c != null && (c.toUpperCase().contains("DIABETES") || c.toUpperCase().contains("TIEU_DUONG")));
    if (isDiabetic) {
      BigDecimal sugarPerServing = food.getSugarPerServing() != null ? food.getSugarPerServing() : BigDecimal.ZERO;
      BigDecimal sugarPer100g = food.getSugarPer100g() != null ? food.getSugarPer100g() : BigDecimal.ZERO;
      if (sugarPerServing.compareTo(BigDecimal.valueOf(10.0)) > 0 || sugarPer100g.compareTo(BigDecimal.valueOf(15.0)) > 0) {
        return false;
      }
    }

    boolean isGout = conditions.stream()
        .anyMatch(c -> c != null && c.toUpperCase().contains("GOUT"));
    if (isGout && hasPurineConflict(food)) {
      return false;
    }

    boolean isKidney = conditions.stream()
        .anyMatch(c -> c != null && (c.toUpperCase().contains("KIDNEY") || c.toUpperCase().contains("THAN")));
    if (isKidney && hasCkdConflict(food)) {
      return false;
    }

    boolean isHypertension = conditions.stream()
        .anyMatch(c -> c != null && (c.toUpperCase().contains("HYPERTENSION") || c.toUpperCase().contains("HUYET_AP")));
    if (isHypertension && hasHypertensionConflict(food)) {
      return false;
    }

    return true;
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

  private boolean isSafeFromAllergies(Food food, Set<String> normalizedAllergies) {
    if (normalizedAllergies == null || normalizedAllergies.isEmpty()) return true;
    return !allergenNormalizer.hasConflict(food.getAllergens(), normalizedAllergies);
  }

  private boolean isSuitableForMealType(Food food, MealType mealType) {
    if (food.getSuitableMealTypes() != null && food.getSuitableMealTypes().contains(mealType)) {
      return true;
    }
    return food.getSuitableMealTypes() == null || food.getSuitableMealTypes().isEmpty();
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

  private void recalculatePlanTotals(MealPlan plan, UserHealthProfile profile) {
    int calo = 0;
    BigDecimal protein = BigDecimal.ZERO;
    BigDecimal carbs = BigDecimal.ZERO;
    BigDecimal fat = BigDecimal.ZERO;
    BigDecimal sugar = BigDecimal.ZERO;
    BigDecimal fiber = BigDecimal.ZERO;
    int cost = 0;
    double simSum = 0.0;

    List<Double> effectivePref = vectorEngine.applyClinicalSafetyMask(profile.getPreferenceVector(), profile.getChronicConditions());

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

  private MealPlanResponse toResponse(MealPlan plan) {
    Map<MealType, List<MealPlanFoodItemResponse>> groupMap = new EnumMap<>(MealType.class);
    for (MealType mt : MealType.values()) {
      groupMap.put(mt, new ArrayList<>());
    }

    for (MealPlanFood item : plan.getItems()) {
      MealPlanFoodItemResponse itemResp = new MealPlanFoodItemResponse(
          item.getId(),
          item.getFood().getId(),
          item.getFood().getName(),
          item.getMealType(),
          item.getServingName(),
          item.getServingGrams(),
          item.getServingMultiplier(),
          item.getCalories(),
          item.getProteinG(),
          item.getCarbsG(),
          item.getFatG(),
          item.getSugarG(),
          item.getFiberG(),
          item.getEstimatedPriceVnd(),
          item.getFood().getImageUrl()
      );
      groupMap.computeIfAbsent(item.getMealType(), k -> new ArrayList<>()).add(itemResp);
    }

    List<MealGroupResponse> mealGroups = new ArrayList<>();
    int target = plan.getTargetCalories();
    mealGroups.add(new MealGroupResponse(MealType.BREAKFAST, (int) Math.round(target * 0.25),
        groupMap.get(MealType.BREAKFAST).stream().mapToInt(MealPlanFoodItemResponse::calories).sum(),
        groupMap.get(MealType.BREAKFAST)));

    mealGroups.add(new MealGroupResponse(MealType.LUNCH, (int) Math.round(target * 0.35),
        groupMap.get(MealType.LUNCH).stream().mapToInt(MealPlanFoodItemResponse::calories).sum(),
        groupMap.get(MealType.LUNCH)));

    mealGroups.add(new MealGroupResponse(MealType.DINNER, (int) Math.round(target * 0.30),
        groupMap.get(MealType.DINNER).stream().mapToInt(MealPlanFoodItemResponse::calories).sum(),
        groupMap.get(MealType.DINNER)));

    mealGroups.add(new MealGroupResponse(MealType.SNACK, (int) Math.round(target * 0.10),
        groupMap.get(MealType.SNACK).stream().mapToInt(MealPlanFoodItemResponse::calories).sum(),
        groupMap.get(MealType.SNACK)));

    return new MealPlanResponse(
        plan.getId(),
        plan.getName(),
        plan.getDescription(),
        plan.getStatus(),
        plan.getType(),
        plan.getPlanDate(),
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
        mealGroups
    );
  }
}
