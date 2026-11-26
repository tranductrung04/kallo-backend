package com.healthcare.kallo.service.plan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.StorageProperties;
import com.healthcare.kallo.dto.request.plan.GenerateMealPlanRequest;
import com.healthcare.kallo.dto.request.plan.SwapMealPlanItemRequest;
import com.healthcare.kallo.dto.response.plan.MealPlanResponse;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.model.food.enums.FoodSource;
import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.model.plan.MealPlan;
import com.healthcare.kallo.model.plan.MealPlanFood;
import com.healthcare.kallo.model.plan.enums.MealPlanStatus;
import com.healthcare.kallo.model.plan.enums.MealPlanType;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.model.profile.enums.Gender;
import com.healthcare.kallo.repository.food.FoodRepository;
import com.healthcare.kallo.repository.nutri.UserFoodLogRepository;
import com.healthcare.kallo.repository.plan.MealPlanRepository;
import com.healthcare.kallo.repository.profile.UserAllergyRepository;
import com.healthcare.kallo.repository.profile.UserChronicConditionRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.act.UserTimeResolver;
import com.healthcare.kallo.service.auth.StorageService;
import com.healthcare.kallo.service.food.FoodImageStorageService;
import com.healthcare.kallo.service.nutri.FoodLogService;
import com.healthcare.kallo.service.nutri.NutriAllergyChecker;
import com.healthcare.kallo.service.plan.engine.RuleBasedMenuGenerator;
import com.healthcare.kallo.service.plan.engine.VectorSimilarityEngine;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MealPlanServiceImplTest {

  private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final Instant FIXED_NOW = Instant.parse("2026-11-25T05:00:00Z");

  @Mock private MealPlanRepository mealPlanRepository;
  @Mock private FoodRepository foodRepository;
  @Mock private UserHealthProfileRepository profileRepository;
  @Mock private UserChronicConditionRepository chronicConditionRepository;
  @Mock private UserFoodLogRepository userFoodLogRepository;
  @Mock private UserAllergyRepository userAllergyRepository;
  @Mock private FoodLogService foodLogService;
  @Mock private UserTimeResolver userTimeResolver;

  private MealPlanServiceImpl service;
  private UUID userId;

  @BeforeEach
  void setUp() {
    VectorSimilarityEngine vectorEngine = new VectorSimilarityEngine();
    service =
        new MealPlanServiceImpl(
            mealPlanRepository,
            foodRepository,
            profileRepository,
            chronicConditionRepository,
            userFoodLogRepository,
            new NutriAllergyChecker(userAllergyRepository),
            foodLogService,
            new RuleBasedMenuGenerator(vectorEngine),
            vectorEngine,
            userTimeResolver,
            new FoodImageStorageService(
                mock(StorageService.class),
                new StorageProperties(
                    "http://localhost:9000", "k", "s", "b", "http://localhost:9000", Duration.ofMinutes(15))),
            Clock.fixed(FIXED_NOW, VN_ZONE));
    userId = UUID.randomUUID();
    lenient().when(userTimeResolver.zoneOf(userId)).thenReturn(VN_ZONE);
  }

  @Test
  void generateRejectsUnsafeCalorieOverrideBelowGenderFloor() {
    UserHealthProfile maleProfile = new UserHealthProfile();
    maleProfile.setUserId(userId);
    maleProfile.setGender(Gender.MALE);
    maleProfile.setDailyCalorieGoal(new BigDecimal("2200.00"));
    maleProfile.setProteinGrams(new BigDecimal("110.00"));
    when(profileRepository.findById(userId)).thenReturn(Optional.of(maleProfile));

    assertThatThrownBy(
            () ->
                service.generate(
                    userId,
                    new GenerateMealPlanRequest(
                        LocalDate.of(2026, 11, 25), 1, 1400, null, null, true, null)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
  }

  @Test
  void applyRequiresSavedStatusAndRejectsExistingMealLogConflicts() {
    UUID planId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 11, 25);
    MealPlan draft = sampleSavedOrDraftPlan(planId, MealPlanStatus.DRAFT, date);
    when(mealPlanRepository.findWithItemsByIdAndUserId(planId, userId))
        .thenReturn(Optional.of(draft));

    assertThatThrownBy(() -> service.apply(userId, planId, date, null))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.MEAL_PLAN_NOT_SAVED);

    draft.setStatus(MealPlanStatus.SAVED);
    Food food = sampleFood(draft.getItems().get(0).getFoodId(), "Phở bò", 450);
    when(foodRepository.findByIdWithIngredients(food.getId())).thenReturn(Optional.of(food));
    when(userAllergyRepository.findAllByIdUserId(userId)).thenReturn(List.of());
    when(chronicConditionRepository.findAllByUserIdWithCondition(userId)).thenReturn(List.of());
    when(userFoodLogRepository.existsByUserIdAndLoggedDateAndMealTypeAndDeletedAtIsNull(
            userId, date, MealType.BREAKFAST))
        .thenReturn(true);

    assertThatThrownBy(() -> service.apply(userId, planId, date, null))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.MEAL_LOG_ALREADY_EXISTS);

    verify(foodLogService, never()).applyMealPlanItems(any(), any());
  }

  @Test
  void swapItemThrowsNoSuitableSwapFoodWhenNoCompatibleCandidateExists() {
    UUID planId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 11, 25);
    MealPlan plan = sampleSavedOrDraftPlan(planId, MealPlanStatus.SAVED, date);
    UUID itemId = plan.getItems().get(0).getId();

    UserHealthProfile profile = new UserHealthProfile();
    profile.setUserId(userId);
    when(mealPlanRepository.findWithItemsByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));
    when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));
    when(chronicConditionRepository.findAllByUserIdWithCondition(userId)).thenReturn(List.of());
    when(foodRepository.findEligibleFoodsForMealPlan(eq(userId), eq(true), eq(true), any(), eq(false)))
        .thenReturn(List.of());

    assertThatThrownBy(
            () -> service.swapItem(userId, planId, itemId, new SwapMealPlanItemRequest(null, null)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NO_SUITABLE_SWAP_FOOD);
  }

  @Test
  void swapItemReplacesTargetItemAndUpdatesPlanTotals() {
    UUID planId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 11, 25);
    MealPlan plan = sampleSavedOrDraftPlan(planId, MealPlanStatus.DRAFT, date);
    plan.setTargetCalories(450);
    UUID itemId = plan.getItems().get(0).getId();

    Food replacement = sampleFood(UUID.randomUUID(), "Bún bò Huế", 460);
    UserHealthProfile profile = new UserHealthProfile();
    profile.setUserId(userId);

    when(mealPlanRepository.findWithItemsByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));
    when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));
    when(chronicConditionRepository.findAllByUserIdWithCondition(userId)).thenReturn(List.of());
    when(foodRepository.findEligibleFoodsForMealPlan(eq(userId), eq(true), eq(true), any(), eq(false)))
        .thenReturn(List.of(replacement));
    when(userFoodLogRepository.findLoggedFoodIdsSince(userId, date.minusDays(2))).thenReturn(List.of());
    when(foodRepository.findAllById(any())).thenReturn(List.of(replacement));
    when(mealPlanRepository.saveAndFlush(any(MealPlan.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    MealPlanResponse res = service.swapItem(userId, planId, itemId, null);

    assertThat(res.totalCalories()).isEqualTo(460);
    assertThat(res.days().get(0).meals().get(0).items().get(0).foodName()).isEqualTo("Bún bò Huế");
  }

  private MealPlan sampleSavedOrDraftPlan(UUID planId, MealPlanStatus status, LocalDate date) {
    MealPlan plan = new MealPlan();
    plan.setId(planId);
    plan.setUserId(userId);
    plan.setName("Test Plan");
    plan.setType(MealPlanType.DAILY);
    plan.setPlanDate(date);
    plan.setDaysCount(1);
    plan.setStatus(status);
    plan.setTargetCalories(1800);
    plan.setTotalCalories(450);

    MealPlanFood item = new MealPlanFood();
    item.setId(UUID.randomUUID());
    item.setMealPlan(plan);
    item.setFoodId(UUID.randomUUID());
    item.setDayIndex(1);
    item.setMealDate(date);
    item.setMealType(MealType.BREAKFAST);
    item.setServingName("1 tô");
    item.setServingGrams(new BigDecimal("450.0"));
    item.setServingMultiplier(new BigDecimal("1.00"));
    item.setCalories(450);
    item.setProteinG(new BigDecimal("28.0"));
    item.setCarbsG(new BigDecimal("55.0"));
    item.setFatG(new BigDecimal("10.0"));
    item.setSugarG(new BigDecimal("3.0"));
    item.setFiberG(new BigDecimal("3.0"));
    item.setEstimatedPriceVnd(45000);
    plan.getItems().add(item);
    return plan;
  }

  private Food sampleFood(UUID foodId, String name, int calories) {
    Food food = new Food();
    ReflectionTestUtils.setField(food, "id", foodId);
    food.setName(name);
    food.setCategory(FoodCategory.NOODLE);
    food.setDefaultServingName("1 tô");
    food.setDefaultServingGrams(new BigDecimal("450.0"));
    food.setCaloriesPerServing(calories);
    food.setProteinPerServing(new BigDecimal("28.0"));
    food.setCarbsPerServing(new BigDecimal("55.0"));
    food.setFatPerServing(new BigDecimal("10.0"));
    food.setSugarPerServing(new BigDecimal("3.0"));
    food.setFiberPerServing(new BigDecimal("3.0"));
    food.setReferencePriceVnd(45000);
    food.setSuitableMealTypes(List.of("BREAKFAST", "LUNCH", "DINNER"));
    food.setSource(FoodSource.SYSTEM);
    return food;
  }
}
