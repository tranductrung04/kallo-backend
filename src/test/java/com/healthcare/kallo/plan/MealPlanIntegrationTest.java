package com.healthcare.kallo.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.act.ActIntegrationTestBase;
import com.healthcare.kallo.dto.response.plan.MealPlanDayResponse;
import com.healthcare.kallo.dto.response.plan.MealPlanItemResponse;
import com.healthcare.kallo.dto.response.plan.MealPlanResponse;
import com.healthcare.kallo.dto.response.plan.MealPlanSlotResponse;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.FoodIngredient;
import com.healthcare.kallo.model.food.Ingredient;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.model.food.enums.FoodSource;
import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.model.nutri.DailySummary;
import com.healthcare.kallo.model.nutri.UserFoodLog;
import com.healthcare.kallo.model.nutri.enums.LogSource;
import com.healthcare.kallo.model.plan.enums.MealPlanStatus;
import com.healthcare.kallo.model.plan.enums.MealPlanType;
import com.healthcare.kallo.model.profile.ChronicCondition;
import com.healthcare.kallo.model.profile.UserAllergy;
import com.healthcare.kallo.model.profile.UserAllergyId;
import com.healthcare.kallo.model.profile.UserChronicCondition;
import com.healthcare.kallo.model.profile.UserChronicConditionId;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.model.profile.enums.ActivityLevel;
import com.healthcare.kallo.model.profile.enums.Gender;
import com.healthcare.kallo.model.profile.enums.GoalType;
import com.healthcare.kallo.repository.food.FoodRepository;
import com.healthcare.kallo.repository.food.IngredientRepository;
import com.healthcare.kallo.repository.nutri.DailySummaryRepository;
import com.healthcare.kallo.repository.nutri.UserFoodLogRepository;
import com.healthcare.kallo.repository.profile.ChronicConditionRepository;
import com.healthcare.kallo.repository.profile.UserAllergyRepository;
import com.healthcare.kallo.repository.profile.UserChronicConditionRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.plan.engine.VectorSimilarityEngine;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

class MealPlanIntegrationTest extends ActIntegrationTestBase {

  private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired FoodRepository foodRepository;
  @Autowired IngredientRepository ingredientRepository;
  @Autowired UserAllergyRepository userAllergyRepository;
  @Autowired ChronicConditionRepository chronicConditionRepository;
  @Autowired UserChronicConditionRepository userChronicConditionRepository;
  @Autowired UserHealthProfileRepository profileRepository;
  @Autowired UserFoodLogRepository foodLogRepository;
  @Autowired DailySummaryRepository dailySummaryRepository;

  @Test
  void migrationSeededChronicConditionsAndAll67VietFoodVectors() {
    List<ChronicCondition> conditions = chronicConditionRepository.findAll();
    assertThat(conditions)
        .extracting(ChronicCondition::getCode)
        .contains("DIABETES_T2", "DIABETES_T1", "HYPERTENSION", "GOUT", "CHRONIC_KIDNEY_DISEASE");

    Food pho = foodRepository.findByVietfoodClassId(46).orElseThrow();
    assertThat(pho.getFeatureVector()).hasSize(12);
    assertThat(pho.getFeatureVector().get(VectorSimilarityEngine.IDX_SOUP_BASED)).isEqualTo(1.0);
    assertThat(pho.getFeatureVector().get(VectorSimilarityEngine.IDX_BEEF)).isEqualTo(1.0);

    Food supCua = foodRepository.findByVietfoodClassId(67).orElseThrow();
    assertThat(supCua.getFeatureVector()).hasSize(12);
    assertThat(supCua.getFeatureVector().get(VectorSimilarityEngine.IDX_SEAFOOD)).isGreaterThan(0.5);
  }

  @Test
  void testGeneratePlan_AllergyFilterStrict_SystemAndRecipe() throws Exception {
    User user = verifiedUser("plan-allergy");
    String token = accessToken(user);
    UserHealthProfile profile = createProfile(user.getId(), 1900, 100);
    LocalDate today = LocalDate.now(VN_ZONE);

    Ingredient peanut = findAllergen("PEANUT");
    Ingredient crustaceans = findAllergen("CRUSTACEANS");
    Ingredient dairy = findAllergen("DAIRY");

    saveAllergy(profile, peanut);
    saveAllergy(profile, crustaceans);
    saveAllergy(profile, dairy);

    Food dairyRecipe =
        createFood(
            "Yến mạch sữa bò custom",
            FoodCategory.STAPLE,
            FoodSource.USER_CUSTOM,
            user.getId(),
            "https://cdn.kallo.app/custom/oat-milk.jpg",
            450,
            25000);
    dairyRecipe
        .getIngredients()
        .add(new FoodIngredient(dairyRecipe, dairy, new BigDecimal("100.0")));
    foodRepository.saveAndFlush(dairyRecipe);

    String json =
        mockMvc
            .perform(
                post("/api/meal-plans/generate")
                    .header("Authorization", "Bearer " + token)
                    .contentType(APPLICATION_JSON)
                    .content("{\"planDate\":\"%s\",\"days\":1}".formatted(today)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    MealPlanResponse res = parsePlanResponse(json);
    Set<UUID> forbiddenFoodIds =
        Set.of(
            dairyRecipe.getId(),
            UUID.fromString("10000000-0000-0000-0000-000000000006"), // Bánh tráng trộn (PEANUT)
            UUID.fromString("10000000-0000-0000-0000-000000000010"), // Bún riêu (CRUSTACEANS)
            UUID.fromString("10000000-0000-0000-0000-00000000001d"), // Cua (CRUSTACEANS)
            UUID.fromString("10000000-0000-0000-0000-00000000002f"), // Phô mai (DAIRY)
            UUID.fromString("10000000-0000-0000-0000-000000000037"), // Tôm (CRUSTACEANS)
            UUID.fromString("10000000-0000-0000-0000-000000000043")); // Súp cua (CRUSTACEANS)

    assertThat(allFoodIds(res)).doesNotContainAnyElementsOf(forbiddenFoodIds);
  }

  @Test
  void testGeneratePlan_ExcludesSoftDeletedOtherUserAndOffFoods() throws Exception {
    User userA = verifiedUser("plan-user-a");
    User userB = verifiedUser("plan-user-b");
    String tokenA = accessToken(userA);
    createProfile(userA.getId(), 1900, 100);
    LocalDate today = LocalDate.now(VN_ZONE);

    Food softDeletedOfA =
        createFood(
            "Món đã xóa của A",
            FoodCategory.NOODLE,
            FoodSource.USER_CUSTOM,
            userA.getId(),
            null,
            450,
            40000);
    softDeletedOfA.setDeletedAt(Instant.now());
    foodRepository.saveAndFlush(softDeletedOfA);

    Food customOfB =
        createFood(
            "Món bí mật của B",
            FoodCategory.RICE,
            FoodSource.USER_CUSTOM,
            userB.getId(),
            null,
            500,
            40000);

    Food offFood =
        createFood(
            "Sữa hộp OFF",
            FoodCategory.SNACK,
            FoodSource.OFF,
            null,
            "https://off.org/milk.jpg",
            180,
            15000);

    Food activeCustomOfA =
        createFood(
            "Bát Yến Mạch Dâu Tây Custom",
            FoodCategory.SNACK,
            FoodSource.USER_CUSTOM,
            userA.getId(),
            "https://cdn.kallo.app/custom/oatmeal.jpg",
            185,
            25000);

    String json =
        mockMvc
            .perform(
                post("/api/meal-plans/generate")
                    .header("Authorization", "Bearer " + tokenA)
                    .contentType(APPLICATION_JSON)
                    .content("{\"planDate\":\"%s\",\"days\":1}".formatted(today)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    MealPlanResponse res = parsePlanResponse(json);
    assertThat(allFoodIds(res))
        .doesNotContain(softDeletedOfA.getId(), customOfB.getId(), offFood.getId());
    assertThat(activeCustomOfA.getImageUrl()).isNotBlank();
  }

  @Test
  void testWeekly7DaysClinicalPlanSaveSwapApplyAndOnlinePreferenceLearning() throws Exception {
    User user = verifiedUser("plan-e2e");
    String token = accessToken(user);
    UserHealthProfile profile = createProfile(user.getId(), 1950, 110);
    LocalDate startDate = LocalDate.now(VN_ZONE).plusDays(1);

    ChronicCondition diabetesT2 = chronicConditionRepository.findByCode("DIABETES_T2").orElseThrow();
    UserChronicCondition ucc = new UserChronicCondition();
    ucc.setId(new UserChronicConditionId(user.getId(), diabetesT2.getId()));
    ucc.setProfile(profile);
    ucc.setCondition(diabetesT2);
    userChronicConditionRepository.saveAndFlush(ucc);

    // 1. Generate 7-day WEEKLY plan with budget cap
    String genJson =
        mockMvc
            .perform(
                post("/api/meal-plans/generate")
                    .header("Authorization", "Bearer " + token)
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {
                          "planDate": "%s",
                          "days": 7,
                          "maxBudgetVnd": 180000
                        }
                        """
                            .formatted(startDate)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.type").value(MealPlanType.WEEKLY.name()))
            .andExpect(jsonPath("$.data.daysCount").value(7))
            .andExpect(jsonPath("$.data.status").value(MealPlanStatus.DRAFT.name()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    MealPlanResponse generated = parsePlanResponse(genJson);
    assertThat(generated.medicalWarning()).contains("DIABETES");
    assertThat(generated.days()).hasSize(7);
    for (MealPlanDayResponse day : generated.days()) {
      assertThat(day.totalCalories()).isBetween(1852, 2048);
      assertThat(day.estimatedCostVnd()).isLessThanOrEqualTo(180000 + 100000);
      assertThat(day.meals().stream().filter(m -> m.mealType() == MealType.BREAKFAST).findFirst())
          .hasValueSatisfying(b -> assertThat(b.items()).hasSize(1));
      assertThat(day.meals().stream().filter(m -> m.mealType() == MealType.LUNCH).findFirst())
          .hasValueSatisfying(l -> assertThat(l.items()).hasSizeBetween(3, 4));
      assertThat(day.meals().stream().filter(m -> m.mealType() == MealType.DINNER).findFirst())
          .hasValueSatisfying(d -> assertThat(d.items()).hasSizeBetween(3, 4));
    }

    // 2. Apply fails before SAVE (409 MEAL_PLAN_NOT_SAVED)
    mockMvc
        .perform(
            post("/api/meal-plans/{id}/apply", generated.id())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("MEAL_PLAN_NOT_SAVED"));

    // 3. Save DRAFT -> SAVED and swap day 1 breakfast item
    mockMvc
        .perform(
            post("/api/meal-plans/{id}/save", generated.id())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("SAVED"));

    MealPlanItemResponse firstItem =
        generated.days().get(0).meals().get(0).items().get(0);
    String swapJson =
        mockMvc
            .perform(
                post("/api/meal-plans/{id}/items/{itemId}/swap", generated.id(), firstItem.id())
                    .header("Authorization", "Bearer " + token)
                    .contentType(APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    MealPlanResponse swapped = parsePlanResponse(swapJson);
    UUID swappedFoodId = swapped.days().get(0).meals().get(0).items().get(0).foodId();
    assertThat(swappedFoodId).isNotEqualTo(firstItem.foodId());

    // 4. Apply day 1 of saved plan to today -> creates food logs & updates summary WITHOUT shifting preferenceVector
    List<Double> prefBeforeApply =
        List.copyOf(profileRepository.findById(user.getId()).orElseThrow().getPreferenceVector());
    LocalDate applyDate = LocalDate.now(VN_ZONE);

    mockMvc
        .perform(
            post("/api/meal-plans/{id}/apply", generated.id())
                .param("date", applyDate.toString())
                .param("dayIndex", "1")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("APPLIED"));

    List<UserFoodLog> appliedLogs =
        foodLogRepository.findByUserIdAndLoggedDateAndDeletedAtIsNullOrderByLoggedAtAsc(
            user.getId(), applyDate);
    assertThat(appliedLogs).isNotEmpty();
    assertThat(appliedLogs).allSatisfy(l -> assertThat(l.getSource()).isEqualTo(LogSource.MEAL_PLAN));

    DailySummary summary =
        dailySummaryRepository.findByUserIdAndSummaryDate(user.getId(), applyDate).orElseThrow();
    assertThat(summary.getCaloriesConsumed()).isPositive();

    List<Double> prefAfterApply =
        profileRepository.findById(user.getId()).orElseThrow().getPreferenceVector();
    assertThat(prefAfterApply).isEqualTo(prefBeforeApply);

    // 5. Real food log via NUTRI triggers online preference adaptation via FoodPreferenceSyncAdapter
    Food pho = foodRepository.findByVietfoodClassId(46).orElseThrow();
    mockMvc
        .perform(
            post("/api/food-logs")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {
                      "foodId": "%s",
                      "mealType": "SNACK",
                      "servingGrams": 500,
                      "servingQuantity": 1.0,
                      "loggedDate": "%s",
                      "clientUuid": "%s"
                    }
                    """
                        .formatted(pho.getId(), applyDate.minusDays(1), UUID.randomUUID())))
        .andExpect(status().isCreated());

    List<Double> prefAfterRealLog =
        profileRepository.findById(user.getId()).orElseThrow().getPreferenceVector();
    assertThat(prefAfterRealLog.get(VectorSimilarityEngine.IDX_BEEF))
        .isGreaterThan(prefAfterApply.get(VectorSimilarityEngine.IDX_BEEF));
  }

  @Test
  void testCannotAccessSwapApplyOrDeleteOtherUserMealPlan() throws Exception {
    User owner = verifiedUser("plan-owner-idor");
    User attacker = verifiedUser("plan-attacker-idor");
    String ownerToken = accessToken(owner);
    String attackerToken = accessToken(attacker);
    createProfile(owner.getId(), 1900, 100);
    LocalDate today = LocalDate.now(VN_ZONE);

    String genJson =
        mockMvc
            .perform(
                post("/api/meal-plans/generate")
                    .header("Authorization", "Bearer " + ownerToken)
                    .contentType(APPLICATION_JSON)
                    .content("{\"planDate\":\"%s\"}".formatted(today)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    MealPlanResponse ownerPlan = parsePlanResponse(genJson);
    UUID itemId = ownerPlan.days().get(0).meals().get(0).items().get(0).id();

    mockMvc
        .perform(
            get("/api/meal-plans/{id}", ownerPlan.id())
                .header("Authorization", "Bearer " + attackerToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("MEAL_PLAN_NOT_FOUND"));

    mockMvc
        .perform(
            post("/api/meal-plans/{id}/save", ownerPlan.id())
                .header("Authorization", "Bearer " + attackerToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("MEAL_PLAN_NOT_FOUND"));

    mockMvc
        .perform(
            post("/api/meal-plans/{id}/items/{itemId}/swap", ownerPlan.id(), itemId)
                .header("Authorization", "Bearer " + attackerToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("MEAL_PLAN_NOT_FOUND"));

    mockMvc
        .perform(
            post("/api/meal-plans/{id}/apply", ownerPlan.id())
                .header("Authorization", "Bearer " + attackerToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("MEAL_PLAN_NOT_FOUND"));

    mockMvc
        .perform(
            delete("/api/meal-plans/{id}", ownerPlan.id())
                .header("Authorization", "Bearer " + attackerToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("MEAL_PLAN_NOT_FOUND"));
  }

  private MealPlanResponse parsePlanResponse(String json) {
    return objectMapper.treeToValue(objectMapper.readTree(json).get("data"), MealPlanResponse.class);
  }

  private Set<UUID> allFoodIds(MealPlanResponse plan) {
    Set<UUID> ids = new HashSet<>();
    for (MealPlanDayResponse day : plan.days()) {
      for (MealPlanSlotResponse slot : day.meals()) {
        for (MealPlanItemResponse item : slot.items()) {
          ids.add(item.foodId());
        }
      }
    }
    return ids;
  }

  private UserHealthProfile createProfile(UUID userId, int dailyCalorieGoal, int proteinGrams) {
    UserHealthProfile profile = new UserHealthProfile();
    profile.setUserId(userId);
    profile.setGender(Gender.MALE);
    profile.setDob(LocalDate.of(1994, 4, 15));
    profile.setHeightCm(new BigDecimal("172.00"));
    profile.setWeightKg(new BigDecimal("65.00"));
    profile.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE);
    profile.setGoalType(GoalType.MAINTAIN_WEIGHT);
    profile.setTargetWeightKg(null);
    profile.setWeeklyRateKg(BigDecimal.ZERO);
    profile.setBmr(new BigDecimal("1550.00"));
    profile.setTdee(BigDecimal.valueOf(dailyCalorieGoal));
    profile.setDailyCalorieGoal(BigDecimal.valueOf(dailyCalorieGoal));
    profile.setCarbGrams(new BigDecimal("210.00"));
    profile.setProteinGrams(BigDecimal.valueOf(proteinGrams));
    profile.setFatGrams(new BigDecimal("55.00"));
    profile.setWaterLiters(new BigDecimal("2.00"));
    return profileRepository.saveAndFlush(profile);
  }

  private Ingredient findAllergen(String code) {
    return ingredientRepository.findAllCommonAllergens().stream()
        .filter(i -> code.equals(i.getCode()))
        .findFirst()
        .orElseThrow();
  }

  private void saveAllergy(UserHealthProfile profile, Ingredient ingredient) {
    UserAllergy ua = new UserAllergy();
    ua.setId(new UserAllergyId(profile.getUserId(), ingredient.getId()));
    ua.setProfile(profile);
    userAllergyRepository.saveAndFlush(ua);
  }

  private Food createFood(
      String name,
      FoodCategory category,
      FoodSource source,
      UUID ownerUserId,
      String imageUrl,
      int cal,
      int priceVnd) {
    Food food = new Food();
    food.setName(name);
    food.setCategory(category);
    food.setDefaultServingName("1 phần");
    food.setDefaultServingGrams(new BigDecimal("200.0"));
    food.setCaloriesPer100g(BigDecimal.valueOf(cal / 2.0));
    food.setProteinPer100g(new BigDecimal("12.0"));
    food.setCarbsPer100g(new BigDecimal("20.0"));
    food.setFatPer100g(new BigDecimal("4.0"));
    food.setSugarPer100g(new BigDecimal("1.5"));
    food.setFiberPer100g(new BigDecimal("2.0"));
    food.setCaloriesPerServing(cal);
    food.setProteinPerServing(new BigDecimal("24.0"));
    food.setCarbsPerServing(new BigDecimal("40.0"));
    food.setFatPerServing(new BigDecimal("8.0"));
    food.setSugarPerServing(new BigDecimal("3.0"));
    food.setFiberPerServing(new BigDecimal("4.0"));
    food.setImageUrl(imageUrl);
    food.setSource(source);
    food.setOwnerUserId(ownerUserId);
    food.setReferencePriceVnd(priceVnd);
    food.setSuitableMealTypes(List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK"));
    food.setFeatureVector(
        List.of(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
    food.setVerified(true);
    return foodRepository.saveAndFlush(food);
  }
}
