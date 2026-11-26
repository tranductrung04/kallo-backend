package com.healthcare.kallo.nutri;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.act.ActIntegrationTestBase;
import com.healthcare.kallo.dto.request.nutri.CreateFoodLogRequest;
import com.healthcare.kallo.dto.response.nutri.DailySummaryResponse;
import com.healthcare.kallo.dto.response.sys.SyncPushResponse;
import com.healthcare.kallo.event.act.ActivityDataChangedEvent;
import com.healthcare.kallo.event.profile.ProfileUpdatedEvent;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.model.food.enums.FoodSource;
import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.model.game.Streak;
import com.healthcare.kallo.model.nutri.DailySummary;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.model.profile.enums.ActivityLevel;
import com.healthcare.kallo.model.profile.enums.Gender;
import com.healthcare.kallo.model.profile.enums.GoalType;
import com.healthcare.kallo.model.sys.enums.NotificationType;
import com.healthcare.kallo.repository.food.FoodRepository;
import com.healthcare.kallo.repository.game.StreakRepository;
import com.healthcare.kallo.repository.nutri.DailySummaryRepository;
import com.healthcare.kallo.repository.nutri.UserFoodLogRepository;
import com.healthcare.kallo.repository.nutri.WaterLogRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.repository.sys.NotificationRepository;
import com.healthcare.kallo.service.nutri.DailySummaryService;
import com.healthcare.kallo.service.nutri.FoodLogService;
import com.healthcare.kallo.service.nutri.NutriImageStorageService;
import com.healthcare.kallo.service.nutri.adapter.FoodLogSyncEntityHandler;
import com.healthcare.kallo.service.nutri.adapter.WaterLogSyncEntityHandler;
import com.healthcare.kallo.service.nutri.client.FoodAiClient;
import com.healthcare.kallo.service.sys.SyncService;
import com.healthcare.kallo.service.sys.sync.SyncApplyStatus;
import com.healthcare.kallo.service.sys.sync.SyncChange;
import com.healthcare.kallo.service.sys.sync.SyncEntityType;
import com.healthcare.kallo.service.sys.sync.SyncOperation;
import com.healthcare.kallo.service.sys.sync.SyncOperationType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class NutriIntegrationTest extends ActIntegrationTestBase {

  private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired FoodRepository foodRepository;
  @Autowired UserHealthProfileRepository profileRepository;
  @Autowired UserFoodLogRepository foodLogRepository;
  @Autowired WaterLogRepository waterLogRepository;
  @Autowired DailySummaryRepository dailySummaryRepository;
  @Autowired DailySummaryService dailySummaryService;
  @Autowired FoodLogService foodLogService;
  @Autowired SyncService syncService;
  @Autowired FoodLogSyncEntityHandler foodLogSyncHandler;
  @Autowired WaterLogSyncEntityHandler waterLogSyncHandler;
  @Autowired StreakRepository streakRepository;
  @Autowired NotificationRepository notificationRepository;
  @Autowired ApplicationEventPublisher events;

  @MockitoBean FoodAiClient foodAiClient;
  @MockitoBean NutriImageStorageService nutriImageStorageService;

  @Test
  void testFoodLogIdempotencyAndTombstone() throws Exception {
    User user = verifiedUser("nutri-idem");
    String token = accessToken(user);
    Food food = createFood("Cơm gà", null, null, 250);
    UUID clientUuid = UUID.randomUUID();
    LocalDate today = LocalDate.now(VN_ZONE);

    String payload =
        """
        {
          "foodId": "%s",
          "mealType": "LUNCH",
          "servingGrams": 100,
          "servingQuantity": 1.0,
          "loggedDate": "%s",
          "clientUuid": "%s"
        }
        """
            .formatted(food.getId(), today, clientUuid);

    String firstJson =
        mockMvc
            .perform(
                post("/api/food-logs")
                    .header("Authorization", "Bearer " + token)
                    .contentType(APPLICATION_JSON)
                    .content(payload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.calories").value(250.0))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID logId = UUID.fromString(objectMapper.readTree(firstJson).get("data").get("id").asString());

    mockMvc
        .perform(
            post("/api/food-logs")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isOk());

    DailySummary summaryAfterReplay =
        dailySummaryRepository.findByUserIdAndSummaryDate(user.getId(), today).orElseThrow();
    assertThat(summaryAfterReplay.getCaloriesConsumed()).isEqualTo(250);

    mockMvc
        .perform(delete("/api/food-logs/{id}", logId).header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/food-logs")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("RECORD_DELETED"));

    DailySummary summaryAfterDelete =
        dailySummaryRepository.findByUserIdAndSummaryDate(user.getId(), today).orElseThrow();
    assertThat(summaryAfterDelete.getCaloriesConsumed()).isZero();
  }

  @Test
  void testRecalculatePessimisticLocking() throws Exception {
    User user = verifiedUser("nutri-lock");
    Food meal1 = createFood("Phở gà", null, null, 400);
    Food meal2 = createFood("Bún chả", null, null, 500);
    LocalDate today = LocalDate.now(VN_ZONE);

    foodLogService.logFood(
        user.getId(),
        new CreateFoodLogRequest(
            meal1.getId(),
            MealType.BREAKFAST,
            new BigDecimal("100"),
            BigDecimal.ONE,
            null,
            today,
            Instant.now(),
            UUID.randomUUID(),
            null,
            null));
    foodLogService.logFood(
        user.getId(),
        new CreateFoodLogRequest(
            meal2.getId(),
            MealType.LUNCH,
            new BigDecimal("100"),
            BigDecimal.ONE,
            null,
            today,
            Instant.now(),
            UUID.randomUUID(),
            null,
            null));

    int threads = 5;
    CountDownLatch startGate = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    List<Callable<DailySummaryResponse>> tasks = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      tasks.add(
          () -> {
            startGate.await();
            return dailySummaryService.recalculate(user.getId(), today);
          });
    }
    List<Future<DailySummaryResponse>> futures = new ArrayList<>();
    for (Callable<DailySummaryResponse> task : tasks) {
      futures.add(pool.submit(task));
    }
    startGate.countDown();

    for (Future<DailySummaryResponse> f : futures) {
      DailySummaryResponse res = f.get();
      assertThat(res.caloriesConsumed()).isEqualTo(900);
      assertThat(res.mealsLoggedCount()).isEqualTo(2);
    }
    pool.shutdownNow();
  }

  @Test
  void testPreventLoggingDeletedAndOtherUserCustomFood() throws Exception {
    User userA = verifiedUser("nutri-owner");
    User userB = verifiedUser("nutri-caller");
    String tokenB = accessToken(userB);
    LocalDate today = LocalDate.now(VN_ZONE);

    Food deletedFood = createFood("Deleted Dish", null, null, 300);
    deletedFood.setDeletedAt(Instant.now());
    foodRepository.saveAndFlush(deletedFood);

    Food customOfUserA = createFood("UserA Secret Dish", userA.getId(), null, 350);

    mockMvc
        .perform(
            post("/api/food-logs")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {
                      "foodId": "%s",
                      "mealType": "LUNCH",
                      "loggedDate": "%s",
                      "clientUuid": "%s"
                    }
                    """
                        .formatted(deletedFood.getId(), today, UUID.randomUUID())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("FOOD_ITEM_DELETED"));

    mockMvc
        .perform(
            post("/api/food-logs")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {
                      "foodId": "%s",
                      "mealType": "LUNCH",
                      "loggedDate": "%s",
                      "clientUuid": "%s"
                    }
                    """
                        .formatted(customOfUserA.getId(), today, UUID.randomUUID())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN_FOOD_ACCESS"));
  }

  @Test
  void testAIRecognitionFiltersClass27AndSupportsClass67() throws Exception {
    User user = verifiedUser("nutri-ai");
    String token = accessToken(user);

    Food food0 =
        foodRepository
            .findByVietfoodClassId(0)
            .orElseGet(() -> createFood("Bánh canh test", null, 0, 380));
    Food food67 =
        foodRepository
            .findByVietfoodClassId(67)
            .orElseGet(() -> createFood("Súp cua test", null, 67, 220));

    byte[] jpegBytes = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01};
    String tempKey = "temp/" + user.getId() + "/scan.jpg";
    when(nutriImageStorageService.uploadTempImage(user.getId(), jpegBytes, "image/jpeg"))
        .thenReturn(tempKey);
    when(nutriImageStorageService.presignedUrlOrNull(tempKey)).thenReturn("https://minio/scan.jpg");
    when(foodAiClient.detect(any(), anyString(), anyString()))
        .thenReturn(
            new FoodAiClient.FoodAiDetectResponse(
                true,
                "Sup cua",
                0.93,
                List.of(
                    new FoodAiClient.DetectedItem("Con nguoi", 0.99),
                    new FoodAiClient.DetectedItem("Banh canh", 0.89),
                    new FoodAiClient.DetectedItem("Sup cua", 0.93))));

    MockMultipartFile file = new MockMultipartFile("image", "dish.jpg", "image/jpeg", jpegBytes);
    String body =
        mockMvc
            .perform(
                multipart("/api/food-logs/recognize")
                    .file(file)
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.tempImageKey").value(tempKey))
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode items = objectMapper.readTree(body).get("data").get("items");
    assertThat(items.get(0).get("classId").asInt()).isEqualTo(67);
    assertThat(items.get(0).get("foodId").asString()).isEqualTo(food67.getId().toString());
    assertThat(items.get(0).get("foodName").asString()).isEqualTo(food67.getName());
    assertThat(items.get(1).get("classId").asInt()).isEqualTo(0);
    assertThat(items.get(1).get("foodId").asString()).isEqualTo(food0.getId().toString());
  }

  @Test
  void testActivityAndProfileEventsUpdateDailySummary() {
    User user = verifiedUser("nutri-events");
    LocalDate today = LocalDate.now(VN_ZONE);

    UserHealthProfile profile = createProfile(user.getId(), 1900, 110);
    events.publishEvent(
        new ProfileUpdatedEvent(user.getId(), new BigDecimal("65.0"), Instant.now()));

    DailySummary first =
        dailySummaryRepository.findByUserIdAndSummaryDate(user.getId(), today).orElseThrow();
    assertThat(first.getTargetCalories()).isEqualTo(1900);

    profile.setDailyCalorieGoal(new BigDecimal("2200.00"));
    profileRepository.saveAndFlush(profile);
    events.publishEvent(
        new ProfileUpdatedEvent(user.getId(), new BigDecimal("64.5"), Instant.now()));
    events.publishEvent(new ActivityDataChangedEvent(user.getId(), today));

    DailySummary updated =
        dailySummaryRepository.findByUserIdAndSummaryDate(user.getId(), today).orElseThrow();
    assertThat(updated.getTargetCalories()).isEqualTo(2200);
  }

  @Test
  void testSyncHandlerContract_FoodAndWaterLogs() {
    User user = verifiedUser("nutri-sync");
    User otherUser = verifiedUser("nutri-other");
    Food publicFood = createFood("Public Noodles", null, null, 300);
    Food otherCustomFood = createFood("Other Secret Dish", otherUser.getId(), null, 500);
    LocalDate today = LocalDate.now(VN_ZONE);
    Instant t1 = Instant.now().minusSeconds(60);
    Instant t2 = t1.plusSeconds(10);
    Instant t3 = t2.plusSeconds(10);

    UUID w1 = UUID.randomUUID();
    UUID w2 = UUID.randomUUID();
    UUID w3 = UUID.randomUUID();

    List<SyncOperation> waterBatch =
        List.of(
            new SyncOperation(
                w1,
                SyncEntityType.WATER_LOG,
                SyncOperationType.UPSERT,
                t1,
                Map.of("amountMl", 250, "loggedDate", today.toString(), "loggedAt", t1.toString())),
            new SyncOperation(
                w2,
                SyncEntityType.WATER_LOG,
                SyncOperationType.UPSERT,
                t2,
                Map.of("amountMl", 300, "loggedDate", today.toString(), "loggedAt", t2.toString())),
            new SyncOperation(
                w3,
                SyncEntityType.WATER_LOG,
                SyncOperationType.UPSERT,
                t3,
                Map.of("amountMl", 450, "loggedDate", today.toString(), "loggedAt", t3.toString())));

    SyncPushResponse firstPush = syncService.push(user.getId(), waterBatch);
    assertThat(firstPush.results())
        .allSatisfy(r -> assertThat(r.status()).isEqualTo(SyncApplyStatus.APPLIED));

    SyncPushResponse replayPush = syncService.push(user.getId(), waterBatch);
    assertThat(replayPush.results())
        .allSatisfy(r -> assertThat(r.status()).isEqualTo(SyncApplyStatus.UNCHANGED));
    assertThat(dailySummaryRepository.findByUserIdAndSummaryDate(user.getId(), today).orElseThrow().getWaterMl())
        .isEqualTo(1000);

    UUID foodLogUuid = UUID.randomUUID();
    UUID forbiddenFoodLogUuid = UUID.randomUUID();
    UUID deletedWhileOfflineUuid = UUID.randomUUID();

    SyncPushResponse mixedPush =
        syncService.push(
            user.getId(),
            List.of(
                new SyncOperation(
                    foodLogUuid,
                    SyncEntityType.FOOD_LOG,
                    SyncOperationType.UPSERT,
                    t2,
                    Map.of(
                        "foodId", publicFood.getId().toString(),
                        "mealType", "BREAKFAST",
                        "servingGrams", 100,
                        "servingQuantity", 1,
                        "loggedDate", today.toString(),
                        "loggedAt", t2.toString())),
                new SyncOperation(
                    forbiddenFoodLogUuid,
                    SyncEntityType.FOOD_LOG,
                    SyncOperationType.UPSERT,
                    t2,
                    Map.of(
                        "foodId", otherCustomFood.getId().toString(),
                        "mealType", "LUNCH",
                        "loggedDate", today.toString(),
                        "loggedAt", t2.toString())),
                new SyncOperation(
                    deletedWhileOfflineUuid,
                    SyncEntityType.WATER_LOG,
                    SyncOperationType.DELETE,
                    t3,
                    Map.of())));

    assertThat(mixedPush.results().get(0).status()).isEqualTo(SyncApplyStatus.APPLIED);
    assertThat(mixedPush.results().get(1).status()).isEqualTo(SyncApplyStatus.REJECTED);
    assertThat(mixedPush.results().get(1).reason()).isEqualTo("FORBIDDEN_REFERENCE");
    assertThat(mixedPush.results().get(2).status()).isEqualTo(SyncApplyStatus.APPLIED);

    // Older UPSERT must lose to server state / pure tombstone (S10)
    SyncPushResponse conflictPush =
        syncService.push(
            user.getId(),
            List.of(
                new SyncOperation(
                    foodLogUuid,
                    SyncEntityType.FOOD_LOG,
                    SyncOperationType.UPSERT,
                    t1,
                    Map.of(
                        "foodId", publicFood.getId().toString(),
                        "mealType", "DINNER",
                        "loggedDate", today.toString(),
                        "loggedAt", t1.toString())),
                new SyncOperation(
                    deletedWhileOfflineUuid,
                    SyncEntityType.WATER_LOG,
                    SyncOperationType.UPSERT,
                    t1,
                    Map.of(
                        "amountMl", 500,
                        "loggedDate", today.toString(),
                        "loggedAt", t1.toString()))));

    assertThat(conflictPush.results().get(0).status())
        .isEqualTo(SyncApplyStatus.CONFLICT_SERVER_WINS);
    assertThat(conflictPush.results().get(1).status())
        .isEqualTo(SyncApplyStatus.CONFLICT_SERVER_WINS);

    List<SyncChange> foodChanges =
        foodLogSyncHandler.changesSince(user.getId(), 0L, Instant.now().plusSeconds(10), 20);
    List<SyncChange> waterChanges =
        waterLogSyncHandler.changesSince(user.getId(), 0L, Instant.now().plusSeconds(10), 20);
    assertThat(foodChanges).hasSize(1);
    assertThat(waterChanges).hasSize(4);
    assertThat(waterChanges)
        .anySatisfy(
            change -> {
              assertThat(change.clientUuid()).isEqualTo(deletedWhileOfflineUuid);
              assertThat(change.operation()).isEqualTo(SyncOperationType.DELETE);
            });
  }

  @Test
  void testGameStreakAndSysCalorieAlertIntegration() throws Exception {
    User user = verifiedUser("nutri-game-sys");
    String token = accessToken(user);
    createProfile(user.getId(), 1500, 100);
    LocalDate today = LocalDate.now(VN_ZONE);

    Food morning = createFood("Phở buổi sáng", null, null, 800);
    Food lunch = createFood("Cơm buổi trưa", null, null, 700);
    Food dinner = createFood("Lẩu buổi tối", null, null, 600);
    Food snack = createFood("Trà sữa", null, null, 250);

    postMeal(token, morning.getId(), MealType.BREAKFAST, today);
    postMeal(token, lunch.getId(), MealType.LUNCH, today);

    Streak streak = awaitStreak(user.getId());
    assertThat(streak.getCurrentStreak()).isGreaterThanOrEqualTo(1);

    postMeal(token, dinner.getId(), MealType.DINNER, today);
    postMeal(token, snack.getId(), MealType.SNACK, today);

    long calorieOverCount =
        notificationRepository
            .findFirstPage(user.getId(), false, PageRequest.of(0, 20))
            .stream()
            .filter(n -> n.getType() == NotificationType.CALORIE_OVER)
            .count();
    assertThat(calorieOverCount).isEqualTo(1L);
  }

  private void postMeal(String token, UUID foodId, MealType mealType, LocalDate date)
      throws Exception {
    mockMvc
        .perform(
            post("/api/food-logs")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {
                      "foodId": "%s",
                      "mealType": "%s",
                      "servingGrams": 100,
                      "servingQuantity": 1.0,
                      "loggedDate": "%s",
                      "clientUuid": "%s"
                    }
                    """
                        .formatted(foodId, mealType.name(), date, UUID.randomUUID())))
        .andExpect(status().isCreated());
  }

  private Streak awaitStreak(UUID userId) throws InterruptedException {
    for (int i = 0; i < 30; i++) {
      Streak streak = streakRepository.findById(userId).orElse(null);
      if (streak != null && streak.getCurrentStreak() >= 1) {
        return streak;
      }
      Thread.sleep(100);
    }
    return streakRepository.findById(userId).orElse(null);
  }

  private Food createFood(String name, UUID ownerUserId, Integer vietfoodClassId, int calories100g) {
    Food food = new Food();
    food.setName(name);
    food.setCategory(FoodCategory.RICE);
    food.setDefaultServingName("1 phần");
    food.setDefaultServingGrams(new BigDecimal("100.0"));
    food.setCaloriesPer100g(BigDecimal.valueOf(calories100g));
    food.setProteinPer100g(new BigDecimal("20.0"));
    food.setCarbsPer100g(new BigDecimal("40.0"));
    food.setFatPer100g(new BigDecimal("10.0"));
    food.setSugarPer100g(new BigDecimal("3.0"));
    food.setFiberPer100g(new BigDecimal("2.0"));
    food.setCaloriesPerServing(calories100g);
    food.setProteinPerServing(new BigDecimal("20.0"));
    food.setCarbsPerServing(new BigDecimal("40.0"));
    food.setFatPerServing(new BigDecimal("10.0"));
    food.setSugarPerServing(new BigDecimal("3.0"));
    food.setFiberPerServing(new BigDecimal("2.0"));
    food.setSource(ownerUserId != null ? FoodSource.USER_CUSTOM : FoodSource.SYSTEM);
    food.setOwnerUserId(ownerUserId);
    food.setVietfoodClassId(vietfoodClassId);
    food.setVerified(true);
    return foodRepository.saveAndFlush(food);
  }

  private UserHealthProfile createProfile(UUID userId, int dailyCalorieGoal, int proteinGrams) {
    UserHealthProfile profile = new UserHealthProfile();
    profile.setUserId(userId);
    profile.setGender(Gender.MALE);
    profile.setDob(LocalDate.of(1995, 5, 10));
    profile.setHeightCm(new BigDecimal("172.00"));
    profile.setWeightKg(new BigDecimal("65.00"));
    profile.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE);
    profile.setGoalType(GoalType.MAINTAIN_WEIGHT);
    profile.setTargetWeightKg(null);
    profile.setWeeklyRateKg(BigDecimal.ZERO);
    profile.setBmr(new BigDecimal("1550.00"));
    profile.setTdee(BigDecimal.valueOf(dailyCalorieGoal));
    profile.setDailyCalorieGoal(BigDecimal.valueOf(dailyCalorieGoal));
    profile.setCarbGrams(new BigDecimal("200.00"));
    profile.setProteinGrams(BigDecimal.valueOf(proteinGrams));
    profile.setFatGrams(new BigDecimal("50.00"));
    profile.setWaterLiters(new BigDecimal("2.00"));
    return profileRepository.saveAndFlush(profile);
  }
}
