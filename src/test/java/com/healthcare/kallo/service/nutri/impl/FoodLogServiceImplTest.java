package com.healthcare.kallo.service.nutri.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.StorageProperties;
import com.healthcare.kallo.dto.request.nutri.CopyMealRequest;
import com.healthcare.kallo.dto.request.nutri.CreateFoodLogRequest;
import com.healthcare.kallo.dto.response.nutri.CopyMealResponse;
import com.healthcare.kallo.dto.response.nutri.FoodLogResponse;
import com.healthcare.kallo.event.sys.MealLoggedEvent;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.FoodIngredient;
import com.healthcare.kallo.model.food.Ingredient;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.model.food.enums.FoodSource;
import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.model.nutri.UserFoodLog;
import com.healthcare.kallo.model.nutri.enums.LogSource;
import com.healthcare.kallo.model.profile.UserAllergy;
import com.healthcare.kallo.model.profile.UserAllergyId;
import com.healthcare.kallo.repository.food.FoodRepository;
import com.healthcare.kallo.repository.nutri.DailySummaryRepository;
import com.healthcare.kallo.repository.nutri.UserFoodLogRepository;
import com.healthcare.kallo.repository.profile.UserAllergyRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.act.UserTimeResolver;
import com.healthcare.kallo.service.auth.StorageService;
import com.healthcare.kallo.service.food.FoodImageStorageService;
import com.healthcare.kallo.service.nutri.DailySummaryService;
import com.healthcare.kallo.service.nutri.FoodLogService;
import com.healthcare.kallo.service.nutri.FoodPreferenceSyncPort;
import com.healthcare.kallo.service.nutri.NutriAllergyChecker;
import com.healthcare.kallo.service.nutri.NutriImageStorageService;
import com.healthcare.kallo.service.sys.sync.SyncApplyResult;
import com.healthcare.kallo.service.sys.sync.SyncApplyStatus;
import com.healthcare.kallo.service.sys.sync.SyncForbiddenReferenceException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FoodLogServiceImplTest {

  private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final Instant FIXED_NOW = Instant.parse("2026-11-25T05:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

  @Mock private UserFoodLogRepository userFoodLogRepository;
  @Mock private FoodRepository foodRepository;
  @Mock private DailySummaryRepository dailySummaryRepository;
  @Mock private UserHealthProfileRepository profileRepository;
  @Mock private DailySummaryService dailySummaryService;
  @Mock private UserAllergyRepository userAllergyRepository;
  @Mock private NutriImageStorageService imageStorageService;
  @Mock private UserTimeResolver userTimeResolver;
  @Mock private ObjectProvider<FoodPreferenceSyncPort> foodPreferenceSyncPort;
  @Mock private ApplicationEventPublisher events;

  private NutriAllergyChecker allergyChecker;
  private FoodLogServiceImpl service;

  private UUID userId;
  private UUID foodId;
  private Food systemFood;

  @BeforeEach
  void setUp() {
    allergyChecker = new NutriAllergyChecker(userAllergyRepository);
    service =
        new FoodLogServiceImpl(
            userFoodLogRepository,
            foodRepository,
            dailySummaryRepository,
            profileRepository,
            dailySummaryService,
            allergyChecker,
            imageStorageService,
            new FoodImageStorageService(
                mock(StorageService.class),
                new StorageProperties(
                    "http://localhost:9000", "k", "s", "b", "http://localhost:9000", Duration.ofMinutes(15))),
            userTimeResolver,
            foodPreferenceSyncPort,
            events,
            Clock.fixed(FIXED_NOW, VN_ZONE));
    userId = UUID.randomUUID();
    foodId = UUID.randomUUID();

    systemFood = new Food();
    ReflectionTestUtils.setField(systemFood, "id", foodId);
    systemFood.setName("Cơm tấm sườn");
    systemFood.setCategory(FoodCategory.RICE);
    systemFood.setDefaultServingName("1 đĩa");
    systemFood.setDefaultServingGrams(new BigDecimal("100.0"));
    systemFood.setCaloriesPer100g(new BigDecimal("200.0"));
    systemFood.setProteinPer100g(new BigDecimal("10.0"));
    systemFood.setCarbsPer100g(new BigDecimal("25.0"));
    systemFood.setFatPer100g(new BigDecimal("6.0"));
    systemFood.setSugarPer100g(new BigDecimal("2.0"));
    systemFood.setFiberPer100g(new BigDecimal("1.5"));
    systemFood.setSource(FoodSource.SYSTEM);

    lenient().when(userTimeResolver.zoneOf(userId)).thenReturn(VN_ZONE);
    lenient().when(userTimeResolver.localDate(FIXED_NOW, VN_ZONE)).thenReturn(TODAY);
  }

  @Test
  void testNutritionSnapshotCalculation() {
    UUID clientUuid = UUID.randomUUID();
    when(userFoodLogRepository.findByUserIdAndClientUuid(userId, clientUuid))
        .thenReturn(Optional.empty(), Optional.of(savedLog(clientUuid, "450.0")));
    when(foodRepository.findByIdWithIngredients(foodId)).thenReturn(Optional.of(systemFood));
    when(userAllergyRepository.findAllByIdUserId(userId)).thenReturn(List.of());
    when(userFoodLogRepository.insertIfAbsent(
            any(),
            eq(userId),
            eq(foodId),
            eq("LUNCH"),
            eq("1 đĩa"),
            eq(new BigDecimal("150.0")),
            eq(new BigDecimal("1.50")),
            eq("SEARCH"),
            eq(TODAY),
            eq(FIXED_NOW),
            eq(null),
            eq(new BigDecimal("450.0")),
            eq(new BigDecimal("22.5")),
            eq(new BigDecimal("56.3")),
            eq(new BigDecimal("13.5")),
            eq(new BigDecimal("4.5")),
            eq(new BigDecimal("3.4")),
            eq(null),
            eq(clientUuid),
            eq(FIXED_NOW),
            eq(FIXED_NOW)))
        .thenReturn(1);

    FoodLogService.CreateResult result =
        service.logFood(
            userId,
            new CreateFoodLogRequest(
                foodId,
                MealType.LUNCH,
                new BigDecimal("150"),
                new BigDecimal("1.5"),
                null,
                TODAY,
                FIXED_NOW,
                clientUuid,
                null,
                null));

    assertThat(result.created()).isTrue();
    assertThat(result.response().calories()).isEqualByComparingTo("450.0");
    verify(dailySummaryService).recalculate(userId, TODAY);
    verify(events).publishEvent(new MealLoggedEvent(userId, "LUNCH", FIXED_NOW));
  }

  @Test
  void testAllergyWarningTriggeredAndCustomIngredientIgnored() {
    UUID peanutId = UUID.randomUUID();
    Ingredient peanut = new Ingredient();
    ReflectionTestUtils.setField(peanut, "id", peanutId);
    peanut.setCode("PEANUT");
    peanut.setName("Đậu phộng");
    peanut.setAllergen(true);
    peanut.setActive(true);
    peanut.setOwnerUserId(null);

    UUID customIngId = UUID.randomUUID();
    Ingredient customIng = new Ingredient();
    ReflectionTestUtils.setField(customIng, "id", customIngId);
    customIng.setCode("CUSTOM_SPICE");
    customIng.setName("Gia vị riêng");
    customIng.setAllergen(true);
    customIng.setActive(true);
    customIng.setOwnerUserId(userId);

    FoodIngredient fi1 = new FoodIngredient();
    fi1.setIngredient(peanut);
    FoodIngredient fi2 = new FoodIngredient();
    fi2.setIngredient(customIng);
    systemFood.setIngredients(List.of(fi1, fi2));

    UserAllergy ua1 = new UserAllergy();
    ua1.setId(new UserAllergyId(userId, peanutId));
    UserAllergy ua2 = new UserAllergy();
    ua2.setId(new UserAllergyId(userId, customIngId));
    when(userAllergyRepository.findAllByIdUserId(userId)).thenReturn(List.of(ua1, ua2));

    UUID clientUuid = UUID.randomUUID();
    when(userFoodLogRepository.findByUserIdAndClientUuid(userId, clientUuid))
        .thenReturn(Optional.empty(), Optional.of(savedLog(clientUuid, "200.0")));
    when(foodRepository.findByIdWithIngredients(foodId)).thenReturn(Optional.of(systemFood));
    when(userFoodLogRepository.insertIfAbsent(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()))
        .thenReturn(1);

    FoodLogService.CreateResult result =
        service.logFood(
            userId,
            new CreateFoodLogRequest(
                foodId, MealType.BREAKFAST, null, null, null, TODAY, FIXED_NOW, clientUuid, null, null));

    assertThat(result.response().hasAllergyWarning()).isTrue();
    assertThat(result.response().allergicIngredients())
        .extracting("code")
        .containsExactly("PEANUT");
  }

  @Test
  void logFoodIdempotencyReplayAndTombstoneConflict() {
    UUID clientUuid = UUID.randomUUID();
    UserFoodLog active = savedLog(clientUuid, "200.0");
    when(userFoodLogRepository.findByUserIdAndClientUuid(userId, clientUuid))
        .thenReturn(Optional.of(active));
    when(foodRepository.findByIdWithIngredients(foodId)).thenReturn(Optional.of(systemFood));

    FoodLogService.CreateResult replay =
        service.logFood(
            userId,
            new CreateFoodLogRequest(
                foodId,
                MealType.LUNCH,
                null,
                null,
                LogSource.AI_PHOTO,
                TODAY,
                FIXED_NOW,
                clientUuid,
                "temp/" + userId + "/abc.jpg",
                null));

    assertThat(replay.created()).isFalse();
    verify(imageStorageService, never()).moveTempToMeals(any(), any());
    verify(dailySummaryService, never()).recalculate(any(), any());

    active.setDeletedAt(FIXED_NOW);
    assertThatThrownBy(
            () ->
                service.logFood(
                    userId,
                    new CreateFoodLogRequest(
                        foodId, MealType.LUNCH, null, null, null, TODAY, FIXED_NOW, clientUuid, null, null)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.RECORD_DELETED);
  }

  @Test
  void logFoodRejectsDeletedFoodAndOtherUserCustomFood() {
    UUID clientUuid = UUID.randomUUID();
    when(userFoodLogRepository.findByUserIdAndClientUuid(userId, clientUuid))
        .thenReturn(Optional.empty());

    Food deletedFood = new Food();
    ReflectionTestUtils.setField(deletedFood, "id", foodId);
    deletedFood.setDeletedAt(FIXED_NOW);
    when(foodRepository.findByIdWithIngredients(foodId)).thenReturn(Optional.empty());
    when(foodRepository.findById(foodId)).thenReturn(Optional.of(deletedFood));

    assertThatThrownBy(
            () ->
                service.logFood(
                    userId,
                    new CreateFoodLogRequest(
                        foodId, MealType.DINNER, null, null, null, TODAY, FIXED_NOW, clientUuid, null, null)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.FOOD_ITEM_DELETED);

    Food otherUsersFood = new Food();
    ReflectionTestUtils.setField(otherUsersFood, "id", foodId);
    otherUsersFood.setOwnerUserId(UUID.randomUUID());
    when(foodRepository.findByIdWithIngredients(foodId)).thenReturn(Optional.of(otherUsersFood));

    assertThatThrownBy(
            () ->
                service.logFood(
                    userId,
                    new CreateFoodLogRequest(
                        foodId, MealType.DINNER, null, null, null, TODAY, FIXED_NOW, clientUuid, null, null)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.FORBIDDEN_FOOD_ACCESS);
  }

  @Test
  void syncUpsertRejectsOtherUsersCustomFoodAndResolvesConflict() {
    UUID clientUuid = UUID.randomUUID();
    Food otherCustom = new Food();
    ReflectionTestUtils.setField(otherCustom, "id", foodId);
    otherCustom.setOwnerUserId(UUID.randomUUID());
    when(foodRepository.findByIdWithIngredients(foodId)).thenReturn(Optional.of(otherCustom));

    assertThatThrownBy(
            () ->
                service.upsertFromSync(
                    userId,
                    clientUuid,
                    FIXED_NOW,
                    Map.of(
                        "foodId", foodId.toString(),
                        "mealType", "BREAKFAST",
                        "loggedAt", FIXED_NOW.toString())))
        .isInstanceOf(SyncForbiddenReferenceException.class);

    when(foodRepository.findByIdWithIngredients(foodId)).thenReturn(Optional.of(systemFood));
    UserFoodLog newerOnServer = savedLog(clientUuid, "200.0");
    newerOnServer.setUpdatedAt(FIXED_NOW.plusSeconds(30));
    when(userFoodLogRepository.findByUserIdAndClientUuid(userId, clientUuid))
        .thenReturn(Optional.of(newerOnServer));

    SyncApplyResult conflict =
        service.upsertFromSync(
            userId,
            clientUuid,
            FIXED_NOW,
            Map.of(
                "foodId", foodId.toString(),
                "mealType", "LUNCH",
                "loggedAt", FIXED_NOW.toString()));

    assertThat(conflict.status()).isEqualTo(SyncApplyStatus.CONFLICT_SERVER_WINS);
    assertThat(conflict.serverUpdatedAt()).isEqualTo(FIXED_NOW.plusSeconds(30));
  }

  @Test
  void copyMealsSkipsDeletedFoodsAndRecalculatesOnce() {
    LocalDate sourceDate = TODAY.minusDays(1);
    UUID deletedFoodId = UUID.randomUUID();

    UserFoodLog activeSrc = savedLog(UUID.randomUUID(), "200.0");
    activeSrc.setLoggedDate(sourceDate);
    UserFoodLog deletedFoodSrc = savedLog(UUID.randomUUID(), "300.0");
    deletedFoodSrc.setFoodId(deletedFoodId);
    deletedFoodSrc.setLoggedDate(sourceDate);

    Food deletedFood = new Food();
    ReflectionTestUtils.setField(deletedFood, "id", deletedFoodId);
    deletedFood.setDeletedAt(FIXED_NOW);

    when(userFoodLogRepository.findByUserIdAndLoggedDateAndDeletedAtIsNullOrderByLoggedAtAsc(
            userId, sourceDate))
        .thenReturn(List.of(activeSrc, deletedFoodSrc));
    when(foodRepository.findAllById(any())).thenReturn(List.of(systemFood, deletedFood));
    when(userAllergyRepository.findAllByIdUserId(userId)).thenReturn(List.of());
    when(userFoodLogRepository.insertIfAbsent(
            any(),
            eq(userId),
            eq(foodId),
            any(),
            any(),
            any(),
            any(),
            eq("COPY"),
            eq(TODAY),
            eq(FIXED_NOW),
            eq(null),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            eq(null),
            any(),
            eq(FIXED_NOW),
            eq(FIXED_NOW)))
        .thenReturn(1);
    when(userFoodLogRepository.findByUserIdAndClientUuid(eq(userId), any()))
        .thenReturn(Optional.of(savedLog(UUID.randomUUID(), "200.0")));

    CopyMealResponse res =
        service.copyMeals(userId, new CopyMealRequest(sourceDate, TODAY, null));

    assertThat(res.copiedCount()).isEqualTo(1);
    assertThat(res.skippedFoods()).hasSize(1);
    assertThat(res.skippedFoods().get(0).foodId()).isEqualTo(deletedFoodId);
    verify(dailySummaryService, times(1)).recalculate(userId, TODAY);
  }

  @Test
  void applyMealPlanItemsRecalculatesDistinctDatesOnce() {
    UUID client1 = UUID.randomUUID();
    UUID client2 = UUID.randomUUID();
    when(foodRepository.findByIdWithIngredients(foodId)).thenReturn(Optional.of(systemFood));
    when(userAllergyRepository.findAllByIdUserId(userId)).thenReturn(List.of());
    when(userFoodLogRepository.insertIfAbsent(
            any(),
            eq(userId),
            eq(foodId),
            any(),
            any(),
            any(),
            any(),
            eq("MEAL_PLAN"),
            eq(TODAY),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()))
        .thenReturn(1);
    when(userFoodLogRepository.findByUserIdAndClientUuid(userId, client1))
        .thenReturn(Optional.of(savedLog(client1, "200.0")));
    when(userFoodLogRepository.findByUserIdAndClientUuid(userId, client2))
        .thenReturn(Optional.of(savedLog(client2, "200.0")));

    List<FoodLogResponse> results =
        service.applyMealPlanItems(
            userId,
            List.of(
                new FoodLogService.MealPlanApplyItem(
                    foodId, MealType.BREAKFAST, null, BigDecimal.ONE, TODAY, FIXED_NOW, client1),
                new FoodLogService.MealPlanApplyItem(
                    foodId, MealType.LUNCH, null, BigDecimal.ONE, TODAY, FIXED_NOW, client2)));

    assertThat(results).hasSize(2);
    verify(dailySummaryService, times(1)).recalculate(userId, TODAY);
  }

  private UserFoodLog savedLog(UUID clientUuid, String calories) {
    UserFoodLog log = new UserFoodLog();
    ReflectionTestUtils.setField(log, "id", UUID.randomUUID());
    log.setUserId(userId);
    log.setFoodId(foodId);
    log.setMealType(MealType.LUNCH);
    log.setServingName("1 đĩa");
    log.setServingGrams(new BigDecimal("100.0"));
    log.setServingQuantity(new BigDecimal("1.00"));
    log.setSource(LogSource.SEARCH);
    log.setLoggedDate(TODAY);
    log.setLoggedAt(FIXED_NOW);
    log.setCalories(new BigDecimal(calories));
    log.setProteinG(new BigDecimal("10.0"));
    log.setCarbsG(new BigDecimal("25.0"));
    log.setFatG(new BigDecimal("6.0"));
    log.setSugarG(new BigDecimal("2.0"));
    log.setFiberG(new BigDecimal("1.5"));
    log.setClientUuid(clientUuid);
    log.setCreatedAt(FIXED_NOW);
    log.setUpdatedAt(FIXED_NOW);
    return log;
  }
}
