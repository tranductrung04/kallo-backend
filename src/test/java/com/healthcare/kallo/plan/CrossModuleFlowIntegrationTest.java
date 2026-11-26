package com.healthcare.kallo.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.act.ActIntegrationTestBase;
import com.healthcare.kallo.dto.response.plan.MealPlanDayResponse;
import com.healthcare.kallo.dto.response.plan.MealPlanItemResponse;
import com.healthcare.kallo.dto.response.plan.MealPlanResponse;
import com.healthcare.kallo.dto.response.plan.MealPlanSlotResponse;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.food.Ingredient;
import com.healthcare.kallo.model.game.Streak;
import com.healthcare.kallo.model.nutri.enums.LogSource;
import com.healthcare.kallo.model.profile.ChronicCondition;
import com.healthcare.kallo.repository.food.IngredientRepository;
import com.healthcare.kallo.repository.game.StreakRepository;
import com.healthcare.kallo.repository.nutri.DailySummaryRepository;
import com.healthcare.kallo.repository.nutri.UserFoodLogRepository;
import com.healthcare.kallo.repository.nutri.WaterLogRepository;
import com.healthcare.kallo.repository.plan.MealPlanRepository;
import com.healthcare.kallo.repository.profile.ChronicConditionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

class CrossModuleFlowIntegrationTest extends ActIntegrationTestBase {

  private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired IngredientRepository ingredientRepository;
  @Autowired ChronicConditionRepository chronicConditionRepository;
  @Autowired UserFoodLogRepository foodLogRepository;
  @Autowired WaterLogRepository waterLogRepository;
  @Autowired DailySummaryRepository dailySummaryRepository;
  @Autowired MealPlanRepository mealPlanRepository;
  @Autowired StreakRepository streakRepository;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void fullJourneyAcrossProfileFoodPlanNutriGameAndCascadeDelete() throws Exception {
    User user = verifiedUser("e2e-stage4");
    String token = accessToken(user);
    LocalDate today = LocalDate.now(VN_ZONE);

    // 1. PROFILE: Upsert health profile via REST
    mockMvc
        .perform(
            put("/api/v1/users/me/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {
                      "gender": "MALE",
                      "dob": "1995-05-10",
                      "heightCm": 172.0,
                      "weightKg": 65.0,
                      "activityLevel": "MODERATELY_ACTIVE",
                      "goalType": "MAINTAIN_WEIGHT",
                      "targetWeightKg": null,
                      "weeklyRateKg": 0.00
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    // 2. PROFILE: Declare CRUSTACEANS allergy & DIABETES_T2 chronic condition via REST
    Ingredient crustaceans =
        ingredientRepository.findAllCommonAllergens().stream()
            .filter(i -> "CRUSTACEANS".equals(i.getCode()))
            .findFirst()
            .orElseThrow();
    mockMvc
        .perform(
            put("/api/v1/users/me/allergies")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {
                      "allergies": [
                        { "ingredientId": "%s", "note": "Shellfish" }
                      ]
                    }
                    """
                        .formatted(crustaceans.getId())))
        .andExpect(status().isOk());

    ChronicCondition diabetes = chronicConditionRepository.findByCode("DIABETES_T2").orElseThrow();
    mockMvc
        .perform(
            put("/api/v1/users/me/chronic-conditions")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {
                      "conditions": [
                        { "conditionId": "%s", "diagnosedAt": "2024-01-15", "note": "Managed" }
                      ]
                    }
                    """
                        .formatted(diabetes.getId())))
        .andExpect(status().isOk());

    // 3. FOOD: Create custom low-sugar snack via REST
    mockMvc
        .perform(
            post("/api/foods/custom")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Hạt hạnh nhân rang bơ",
                      "category": "SNACK",
                      "defaultServingName": "1 gói",
                      "defaultServingGrams": 40.0,
                      "caloriesPer100g": 550.0,
                      "proteinPer100g": 20.0,
                      "carbsPer100g": 20.0,
                      "fatPer100g": 45.0,
                      "sugarPer100g": 3.0,
                      "fiberPer100g": 10.0,
                      "suitableMealTypes": ["SNACK"]
                    }
                    """))
        .andExpect(status().isCreated());

    // 4. PLAN: Generate personalized plan -> Save -> Apply to today
    String genJson =
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
    MealPlanResponse generated =
        objectMapper.treeToValue(objectMapper.readTree(genJson).get("data"), MealPlanResponse.class);

    assertThat(generated.medicalWarning()).contains("DIABETES");
    assertThat(generated.totalSugarG()).isLessThanOrEqualTo(new BigDecimal("25.0"));
    for (MealPlanDayResponse day : generated.days()) {
      for (MealPlanSlotResponse slot : day.meals()) {
        for (MealPlanItemResponse item : slot.items()) {
          assertThat(item.foodId())
              .isNotEqualTo(UUID.fromString("10000000-0000-0000-0000-000000000037")); // Tôm
          assertThat(item.foodId())
              .isNotEqualTo(UUID.fromString("10000000-0000-0000-0000-00000000001d")); // Cua
        }
      }
    }

    mockMvc
        .perform(
            post("/api/meal-plans/{id}/save", generated.id())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("SAVED"));

    mockMvc
        .perform(
            post("/api/meal-plans/{id}/apply", generated.id())
                .param("date", today.toString())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("APPLIED"));

    // 5. NUTRI: Water log + Daily summary reflects applied meals + water
    mockMvc
        .perform(
            post("/api/water-logs")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {
                      "amountMl": 400,
                      "loggedDate": "%s",
                      "clientUuid": "%s"
                    }
                    """
                        .formatted(today, UUID.randomUUID())))
        .andExpect(status().isCreated());

    String summaryJson =
        mockMvc
            .perform(get("/api/daily-summaries/today").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.waterMl").value(400))
            .andExpect(jsonPath("$.data.goalAchieved").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    int summaryCalories = objectMapper.readTree(summaryJson).get("data").get("caloriesConsumed").asInt();
    assertThat(summaryCalories).isCloseTo(generated.totalCalories(), org.assertj.core.data.Offset.offset(15));

    assertThat(
            foodLogRepository.findByUserIdAndLoggedDateAndDeletedAtIsNullOrderByLoggedAtAsc(
                user.getId(), today))
        .allSatisfy(log -> assertThat(log.getSource()).isEqualTo(LogSource.MEAL_PLAN));

    // 6. GAME: Async streak recompute triggered from DailyProgressChangedEvent
    Streak streak = null;
    for (int i = 0; i < 30; i++) {
      streak = streakRepository.findById(user.getId()).orElse(null);
      if (streak != null && streak.getCurrentStreak() >= 1) {
        break;
      }
      Thread.sleep(100);
    }
    assertThat(streak).isNotNull();
    assertThat(streak.getCurrentStreak()).isGreaterThanOrEqualTo(1);

    // 7. CASCADE DELETE: Removing user cleans up PLAN, NUTRI and PROFILE records cleanly
    jdbcTemplate.update("DELETE FROM users WHERE id = ?", user.getId());
    assertThat(mealPlanRepository.findByIdAndUserId(generated.id(), user.getId())).isEmpty();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_food_logs WHERE user_id = ?",
                Long.class,
                user.getId()))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM water_logs WHERE user_id = ?", Long.class, user.getId()))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM daily_summaries WHERE user_id = ?",
                Long.class,
                user.getId()))
        .isZero();
  }
}
