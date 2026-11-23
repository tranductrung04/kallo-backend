package com.healthcare.kallo.service.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.request.plan.GenerateMealPlanRequest;
import com.healthcare.kallo.dto.request.plan.SaveMealPlanRequest;
import com.healthcare.kallo.dto.request.plan.SwapFoodItemRequest;
import com.healthcare.kallo.dto.response.plan.ApplyMealPlanResponse;
import com.healthcare.kallo.dto.response.plan.MealPlanResponse;
import com.healthcare.kallo.dto.response.report.DailySummaryResponse;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.model.plan.MealPlan;
import com.healthcare.kallo.model.plan.MealPlanFood;
import com.healthcare.kallo.model.plan.enums.MealPlanStatus;
import com.healthcare.kallo.model.plan.enums.MealPlanType;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.food.FoodRepository;
import com.healthcare.kallo.repository.nutri.UserFoodLogRepository;
import com.healthcare.kallo.repository.plan.MealPlanFoodRepository;
import com.healthcare.kallo.repository.plan.MealPlanRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.plan.engine.RuleBasedMenuGenerator;
import com.healthcare.kallo.service.plan.engine.VectorSimilarityEngine;
import com.healthcare.kallo.service.plan.impl.MealPlanServiceImpl;
import com.healthcare.kallo.service.report.DailySummaryService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.healthcare.kallo.service.act.UserTimeResolver;
import com.healthcare.kallo.service.food.AllergenNormalizer;

@ExtendWith(MockitoExtension.class)
class MealPlanServiceTest {

  @Mock private MealPlanRepository mealPlanRepository;
  @Mock private MealPlanFoodRepository mealPlanFoodRepository;
  @Mock private FoodRepository foodRepository;
  @Mock private UserHealthProfileRepository userHealthProfileRepository;
  @Mock private UserRepository userRepository;
  @Mock private UserFoodLogRepository userFoodLogRepository;
  @Mock private DailySummaryService dailySummaryService;
  @Mock private RuleBasedMenuGenerator menuGenerator;
  @Mock private VectorSimilarityEngine vectorEngine;

  private MealPlanService mealPlanService;
  private UUID userId;
  private User user;
  private UserHealthProfile profile;

  @BeforeEach
  void setUp() {
    mealPlanService = new MealPlanServiceImpl(
        mealPlanRepository,
        mealPlanFoodRepository,
        foodRepository,
        userHealthProfileRepository,
        userRepository,
        userFoodLogRepository,
        dailySummaryService,
        menuGenerator,
        vectorEngine
    );

    userId = UUID.randomUUID();
    user = new User();
    user.setId(userId);
    user.setEmail("test@kallo.vn");

    profile = new UserHealthProfile();
    profile.setUser(user);
    profile.setGender(com.healthcare.kallo.model.profile.enums.Gender.MALE);
    profile.setDailyCalorieGoal(2000);
    profile.setProteinGoalG(BigDecimal.valueOf(120));
    profile.setCarbsGoalG(BigDecimal.valueOf(250));
    profile.setFatGoalG(BigDecimal.valueOf(60));
  }

  @Test
  @DisplayName("generateMealPlan success with valid profile and food list")
  void generateMealPlan_success() {
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(userHealthProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(foodRepository.findAvailableFoodsForUser(userId)).thenReturn(List.of(new Food()));
    when(userFoodLogRepository.findRecentFoods(eq(userId), any())).thenReturn(List.of());

    MealPlan generated = new MealPlan();
    generated.setId(UUID.randomUUID());
    generated.setUser(user);
    generated.setPlanDate(LocalDate.now());
    generated.setStatus(MealPlanStatus.DRAFT);
    generated.setType(MealPlanType.DAILY);
    generated.setTargetCalories(2000);
    generated.setTotalCalories(1980);

    when(menuGenerator.generate(any())).thenReturn(generated);
    when(mealPlanRepository.save(any())).thenReturn(generated);

    MealPlanResponse response = mealPlanService.generateMealPlan(userId, new GenerateMealPlanRequest(LocalDate.now(), 1, null, null, null));

    assertThat(response).isNotNull();
    assertThat(response.totalCalories()).isEqualTo(1980);
    assertThat(response.status()).isEqualTo(MealPlanStatus.DRAFT);
  }

  @Test
  @DisplayName("applyMealPlan success converts items to UserFoodLog and recalculates summary")
  void applyMealPlan_success() {
    UUID planId = UUID.randomUUID();
    LocalDate planDate = LocalDate.now();

    MealPlan plan = new MealPlan();
    plan.setId(planId);
    plan.setUser(user);
    plan.setPlanDate(planDate);
    plan.setStatus(MealPlanStatus.SAVED);

    Food food = new Food();
    food.setId(UUID.randomUUID());
    food.setName("Phở bò");
    food.setDefaultServingName("1 tô");

    MealPlanFood item = new MealPlanFood();
    item.setId(UUID.randomUUID());
    item.setFood(food);
    item.setMealType(MealType.BREAKFAST);
    item.setServingName("1 tô");
    item.setServingGrams(BigDecimal.valueOf(400));
    item.setServingMultiplier(BigDecimal.ONE);
    item.setCalories(450);
    item.setProteinG(BigDecimal.valueOf(28));
    item.setCarbsG(BigDecimal.valueOf(50));
    item.setFatG(BigDecimal.valueOf(12));
    plan.addItem(item);

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(userHealthProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userFoodLogRepository.existsByUserIdAndLoggedDateAndMealType(eq(userId), eq(planDate), any())).thenReturn(false);
    when(mealPlanRepository.findByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));
    when(mealPlanRepository.markPlanAppliedAtomically(eq(planId), eq(userId), any(), eq(planDate))).thenReturn(1);

    DailySummaryResponse summaryResponse = new DailySummaryResponse(
        UUID.randomUUID(), planDate, 450, 2000, 1550, BigDecimal.valueOf(28), BigDecimal.valueOf(50),
        BigDecimal.valueOf(12), BigDecimal.ZERO, BigDecimal.ZERO,
        0, 2000, 0, 0, null, true, false, false, 1
    );
    when(dailySummaryService.getSummary(userId, planDate)).thenReturn(summaryResponse);

    ApplyMealPlanResponse result = mealPlanService.applyMealPlan(userId, planId, planDate);

    assertThat(result).isNotNull();
    assertThat(result.logsCreatedCount()).isEqualTo(1);
    assertThat(result.totalCaloriesLogged()).isEqualTo(450);
    assertThat(plan.getStatus()).isEqualTo(MealPlanStatus.APPLIED);
    verify(dailySummaryService).recalculate(userId, planDate);
    verify(userFoodLogRepository).saveAll(any());
  }

  @Test
  @DisplayName("applyMealPlan throws MEAL_PLAN_NOT_SAVED when plan is still DRAFT")
  void applyMealPlan_draftThrows() {
    UUID planId = UUID.randomUUID();
    MealPlan plan = new MealPlan();
    plan.setId(planId);
    plan.setUser(user);
    plan.setStatus(MealPlanStatus.DRAFT);

    when(mealPlanRepository.findByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> mealPlanService.applyMealPlan(userId, planId, LocalDate.now()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.MEAL_PLAN_NOT_SAVED);
  }

  @Test
  @DisplayName("applyMealPlan throws MEAL_PLAN_ALREADY_APPLIED when plan is already APPLIED")
  void applyMealPlan_alreadyAppliedThrows() {
    UUID planId = UUID.randomUUID();
    MealPlan plan = new MealPlan();
    plan.setId(planId);
    plan.setUser(user);
    plan.setStatus(MealPlanStatus.APPLIED);

    when(mealPlanRepository.findByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> mealPlanService.applyMealPlan(userId, planId, LocalDate.now()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.MEAL_PLAN_ALREADY_APPLIED);
  }

  @Test
  @DisplayName("generateMealPlan throws VALIDATION_ERROR when days > 1")
  void generateMealPlan_daysGreaterThanOne_throws() {
    GenerateMealPlanRequest req = new GenerateMealPlanRequest(LocalDate.now(), 3, null, null, null);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> mealPlanService.generateMealPlan(userId, req))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
  }

  @Test
  @DisplayName("deleteMealPlan throws MEAL_PLAN_ALREADY_APPLIED when plan is APPLIED")
  void deleteMealPlan_applied_throws() {
    UUID planId = UUID.randomUUID();
    MealPlan plan = new MealPlan();
    plan.setId(planId);
    plan.setUser(user);
    plan.setStatus(MealPlanStatus.APPLIED);

    when(mealPlanRepository.findByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> mealPlanService.deleteMealPlan(userId, planId))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.MEAL_PLAN_ALREADY_APPLIED);
  }

  @Test
  @DisplayName("swapItem throws MEAL_PLAN_ALREADY_APPLIED when plan is APPLIED")
  void swapItem_applied_throws() {
    UUID planId = UUID.randomUUID();
    MealPlan plan = new MealPlan();
    plan.setId(planId);
    plan.setUser(user);
    plan.setStatus(MealPlanStatus.APPLIED);

    when(mealPlanRepository.findByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> mealPlanService.swapItem(userId, planId, UUID.randomUUID(), null))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.MEAL_PLAN_ALREADY_APPLIED);
  }

  @Test
  @DisplayName("swapItem throws INSUFFICIENT_FOOD_DATA when explicit replacement food is unsuitable for meal type")
  void swapItem_unsuitableMealType_throws() {
    UUID planId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID altFoodId = UUID.randomUUID();

    MealPlan plan = new MealPlan();
    plan.setId(planId);
    plan.setUser(user);
    plan.setStatus(MealPlanStatus.SAVED);

    MealPlanFood item = new MealPlanFood();
    item.setId(itemId);
    item.setMealType(MealType.BREAKFAST);
    plan.addItem(item);

    Food dinnerOnlyFood = new Food();
    dinnerOnlyFood.setId(altFoodId);
    dinnerOnlyFood.setSuitableMealTypes(List.of(MealType.DINNER));

    when(mealPlanRepository.findByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));
    when(userHealthProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(foodRepository.findVisibleFoodById(altFoodId, userId)).thenReturn(Optional.of(dinnerOnlyFood));

    SwapFoodItemRequest request = new SwapFoodItemRequest(altFoodId);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> mealPlanService.swapItem(userId, planId, itemId, request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INSUFFICIENT_FOOD_DATA);
  }

  @Test
  @DisplayName("swapItem throws INSUFFICIENT_FOOD_DATA when replacement food makes cumulative sugar exceed 25g for diabetics")
  void swapItem_diabeticCumulativeSugar_throws() {
    UUID planId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID altFoodId = UUID.randomUUID();

    profile.setChronicConditions(List.of("DIABETES"));

    MealPlan plan = new MealPlan();
    plan.setId(planId);
    plan.setUser(user);
    plan.setStatus(MealPlanStatus.SAVED);

    // Item 1: target item to be replaced (currently 2g sugar)
    MealPlanFood item1 = new MealPlanFood();
    item1.setId(itemId);
    item1.setMealType(MealType.BREAKFAST);
    item1.setServingMultiplier(BigDecimal.ONE);
    item1.setSugarG(BigDecimal.valueOf(2.0));
    plan.addItem(item1);

    // Item 2: other item in plan with 20g sugar (total sugar without item 1 = 20g)
    MealPlanFood item2 = new MealPlanFood();
    item2.setId(UUID.randomUUID());
    item2.setMealType(MealType.LUNCH);
    item2.setServingMultiplier(BigDecimal.ONE);
    item2.setSugarG(BigDecimal.valueOf(20.0));
    plan.addItem(item2);

    // Alternative food has 8g sugar (8g <= 10g individual limit, but 20g + 8g = 28g > 25g cumulative limit!)
    Food altFood = new Food();
    altFood.setId(altFoodId);
    altFood.setSuitableMealTypes(List.of(MealType.BREAKFAST));
    altFood.setSugarPerServing(BigDecimal.valueOf(8.0));
    altFood.setSugarPer100g(BigDecimal.valueOf(8.0));

    when(mealPlanRepository.findByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));
    when(userHealthProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(foodRepository.findVisibleFoodById(altFoodId, userId)).thenReturn(Optional.of(altFood));

    SwapFoodItemRequest request = new SwapFoodItemRequest(altFoodId);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> mealPlanService.swapItem(userId, planId, itemId, request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INSUFFICIENT_FOOD_DATA);
  }

  @Test
  @DisplayName("generateMealPlan uses user timezone when request planDate is null")
  void generateMealPlan_nullPlanDate_resolvesViaUserTimezone() {
    // 18:30 UTC on 2026-11-23 corresponds to 01:30 on 2026-11-24 in Asia/Ho_Chi_Minh (UTC+7)
    Instant fixedInstant = Instant.parse("2026-11-23T18:30:00Z");
    Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
    UserTimeResolver timeResolver = new UserTimeResolver(userRepository, fixedClock);

    MealPlanService tzService = new MealPlanServiceImpl(
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
        timeResolver,
        fixedClock
    );

    user.setTimezone("Asia/Ho_Chi_Minh");
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(userHealthProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(foodRepository.findAvailableFoodsForUser(userId)).thenReturn(List.of(new Food()));
    when(userFoodLogRepository.findRecentFoods(eq(userId), any())).thenReturn(List.of());

    MealPlan generated = new MealPlan();
    generated.setId(UUID.randomUUID());
    generated.setUser(user);
    generated.setPlanDate(LocalDate.of(2026, 11, 24));
    generated.setStatus(MealPlanStatus.DRAFT);
    generated.setType(MealPlanType.DAILY);
    generated.setTargetCalories(2000);
    generated.setTotalCalories(2000);

    when(menuGenerator.generate(any())).thenReturn(generated);
    when(mealPlanRepository.save(any())).thenReturn(generated);

    // Pass null planDate in request
    GenerateMealPlanRequest req = new GenerateMealPlanRequest(null, 1, null, null, null);
    tzService.generateMealPlan(userId, req);

    ArgumentCaptor<RuleBasedMenuGenerator.PlanGenerationInput> captor =
        ArgumentCaptor.forClass(RuleBasedMenuGenerator.PlanGenerationInput.class);
    verify(menuGenerator).generate(captor.capture());

    // Expect date in Vietnam (UTC+7) to be 2026-11-24, not UTC date 2026-11-23
    assertThat(captor.getValue().planDate()).isEqualTo(LocalDate.of(2026, 11, 24));
  }

  @Test
  @DisplayName("swapItem throws INSUFFICIENT_FOOD_DATA when replacement breaks +/- 5% calorie tolerance")
  void swapItem_breaksCalorieTolerance_throws() {
    UUID planId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID altFoodId = UUID.randomUUID();

    MealPlan plan = new MealPlan();
    plan.setId(planId);
    plan.setUser(user);
    plan.setStatus(MealPlanStatus.SAVED);
    plan.setTargetCalories(2000);

    // Target item has 400 kcal
    MealPlanFood targetItem = new MealPlanFood();
    targetItem.setId(itemId);
    targetItem.setMealType(MealType.BREAKFAST);
    targetItem.setServingMultiplier(BigDecimal.ONE);
    targetItem.setCalories(400);
    plan.addItem(targetItem);

    // Other item has 1550 kcal (total = 1950 kcal, within 2000 +/- 5%)
    MealPlanFood otherItem = new MealPlanFood();
    otherItem.setId(UUID.randomUUID());
    otherItem.setMealType(MealType.LUNCH);
    otherItem.setServingMultiplier(BigDecimal.ONE);
    otherItem.setCalories(1550);
    plan.addItem(otherItem);

    // Replacement food has 1200 kcal per serving -> at 0.75x it is 900 kcal -> total 2450 kcal > 2100 kcal
    Food hugeFood = new Food();
    hugeFood.setId(altFoodId);
    hugeFood.setCategory(FoodCategory.RICE_DISH);
    hugeFood.setSuitableMealTypes(List.of(MealType.BREAKFAST));
    hugeFood.setCaloriesPerServing(1200);

    when(mealPlanRepository.findByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));
    when(userHealthProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(foodRepository.findVisibleFoodById(altFoodId, userId)).thenReturn(Optional.of(hugeFood));

    SwapFoodItemRequest request = new SwapFoodItemRequest(altFoodId);

    assertThatThrownBy(() -> mealPlanService.swapItem(userId, planId, itemId, request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INSUFFICIENT_FOOD_DATA);
  }

  @Test
  @DisplayName("swapItem throws INSUFFICIENT_FOOD_DATA when replacement food category is NON_FOOD")
  void swapItem_nonFoodCategory_throws() {
    UUID planId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID altFoodId = UUID.randomUUID();

    MealPlan plan = new MealPlan();
    plan.setId(planId);
    plan.setUser(user);
    plan.setStatus(MealPlanStatus.SAVED);

    MealPlanFood targetItem = new MealPlanFood();
    targetItem.setId(itemId);
    targetItem.setMealType(MealType.BREAKFAST);
    plan.addItem(targetItem);

    Food nonFood = new Food();
    nonFood.setId(altFoodId);
    nonFood.setCategory(FoodCategory.NON_FOOD);

    when(mealPlanRepository.findByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));
    when(userHealthProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(foodRepository.findVisibleFoodById(altFoodId, userId)).thenReturn(Optional.of(nonFood));

    SwapFoodItemRequest request = new SwapFoodItemRequest(altFoodId);

    assertThatThrownBy(() -> mealPlanService.swapItem(userId, planId, itemId, request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INSUFFICIENT_FOOD_DATA);
  }

  @Test
  @DisplayName("swapItem throws INSUFFICIENT_FOOD_DATA when replacement violates maxBudgetVnd")
  void swapItem_exceedsMaxBudget_throws() {
    UUID planId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID altFoodId = UUID.randomUUID();

    MealPlan plan = new MealPlan();
    plan.setId(planId);
    plan.setUser(user);
    plan.setStatus(MealPlanStatus.SAVED);
    plan.setTargetCalories(2000);
    plan.setMaxBudgetVnd(100000);

    MealPlanFood targetItem = new MealPlanFood();
    targetItem.setId(itemId);
    targetItem.setMealType(MealType.BREAKFAST);
    targetItem.setServingMultiplier(BigDecimal.ONE);
    targetItem.setCalories(450);
    targetItem.setEstimatedPriceVnd(30000);
    plan.addItem(targetItem);

    MealPlanFood otherItem = new MealPlanFood();
    otherItem.setId(UUID.randomUUID());
    otherItem.setMealType(MealType.LUNCH);
    otherItem.setServingMultiplier(BigDecimal.ONE);
    otherItem.setCalories(1500);
    otherItem.setEstimatedPriceVnd(80000);
    plan.addItem(otherItem);

    // Expensive food: 50,000 VND -> total cost = 80,000 + 50,000 = 130,000 > 100,000 VND budget
    Food expensiveFood = new Food();
    expensiveFood.setId(altFoodId);
    expensiveFood.setCategory(FoodCategory.RICE_DISH);
    expensiveFood.setSuitableMealTypes(List.of(MealType.BREAKFAST));
    expensiveFood.setCaloriesPerServing(450);
    expensiveFood.setReferencePriceVnd(50000);

    when(mealPlanRepository.findByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));
    when(userHealthProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(foodRepository.findVisibleFoodById(altFoodId, userId)).thenReturn(Optional.of(expensiveFood));

    SwapFoodItemRequest request = new SwapFoodItemRequest(altFoodId);

    assertThatThrownBy(() -> mealPlanService.swapItem(userId, planId, itemId, request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INSUFFICIENT_FOOD_DATA);
  }

  @Test
  @DisplayName("generateMealPlan throws VALIDATION_ERROR when calorieTargetOverride is below safe clinical floor")
  void generateMealPlan_calorieTargetOverrideBelowFloor_throwsValidationError() {
    profile.setGender(com.healthcare.kallo.model.profile.enums.Gender.FEMALE); // floor = 1200 kcal

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(userHealthProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

    GenerateMealPlanRequest req = new GenerateMealPlanRequest(LocalDate.now(), 1, 800, null, null);

    assertThatThrownBy(() -> mealPlanService.generateMealPlan(userId, req))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
  }

  @Test
  @DisplayName("applyMealPlan throws INSUFFICIENT_FOOD_DATA when user health profile was updated with a new allergy")
  void applyMealPlan_profileOutdatedWithNewAllergy_throwsInsufficientFoodData() {
    UUID planId = UUID.randomUUID();
    LocalDate planDate = LocalDate.now();

    MealPlan plan = new MealPlan();
    plan.setId(planId);
    plan.setUser(user);
    plan.setPlanDate(planDate);
    plan.setStatus(MealPlanStatus.SAVED);

    Food food = new Food();
    food.setId(UUID.randomUUID());
    food.setName("Phở bò");
    food.setAllergens(List.of("BEEF"));

    MealPlanFood item = new MealPlanFood();
    item.setId(UUID.randomUUID());
    item.setFood(food);
    item.setMealType(MealType.BREAKFAST);
    plan.addItem(item);

    // Profile now has BEEF allergy
    profile.setAllergies(List.of("BEEF"));

    when(mealPlanRepository.findByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(userHealthProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

    assertThatThrownBy(() -> mealPlanService.applyMealPlan(userId, planId, planDate))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INSUFFICIENT_FOOD_DATA);
  }

  @Test
  @DisplayName("applyMealPlan throws MEAL_LOG_ALREADY_EXISTS when diary already has meals for that meal type")
  void applyMealPlan_existingDiaryLogsForMealType_throwsMealLogAlreadyExists() {
    UUID planId = UUID.randomUUID();
    LocalDate planDate = LocalDate.now();

    MealPlan plan = new MealPlan();
    plan.setId(planId);
    plan.setUser(user);
    plan.setPlanDate(planDate);
    plan.setStatus(MealPlanStatus.SAVED);

    Food food = new Food();
    food.setId(UUID.randomUUID());
    food.setName("Yến mạch");

    MealPlanFood item = new MealPlanFood();
    item.setId(UUID.randomUUID());
    item.setFood(food);
    item.setMealType(MealType.BREAKFAST);
    plan.addItem(item);

    when(mealPlanRepository.findByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(userHealthProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userFoodLogRepository.existsByUserIdAndLoggedDateAndMealType(userId, planDate, MealType.BREAKFAST)).thenReturn(true);

    assertThatThrownBy(() -> mealPlanService.applyMealPlan(userId, planId, planDate))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.MEAL_LOG_ALREADY_EXISTS);
  }
}
