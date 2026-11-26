package com.healthcare.kallo.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.model.act.WeightLog;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.model.nutri.DailySummary;
import com.healthcare.kallo.model.nutri.UserFoodLog;
import com.healthcare.kallo.model.profile.ChronicCondition;
import com.healthcare.kallo.model.profile.UserAllergy;
import com.healthcare.kallo.model.profile.UserAllergyId;
import com.healthcare.kallo.model.profile.UserChronicCondition;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.model.profile.enums.ActivityLevel;
import com.healthcare.kallo.model.profile.enums.Gender;
import com.healthcare.kallo.model.profile.enums.GoalType;
import com.healthcare.kallo.repository.act.WeightLogRepository;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.food.FoodRepository;
import com.healthcare.kallo.repository.nutri.DailySummaryRepository;
import com.healthcare.kallo.repository.nutri.UserFoodLogRepository;
import com.healthcare.kallo.repository.profile.UserAllergyRepository;
import com.healthcare.kallo.repository.profile.UserChronicConditionRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.act.ProfileBodyMetrics;
import com.healthcare.kallo.service.game.DailyProgressProvider;
import com.healthcare.kallo.service.profile.ProfileDomainService;
import com.healthcare.kallo.service.profile.port.IngredientLookup;
import com.healthcare.kallo.service.profile.port.IngredientSummary;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class HealthChatContextProviderTest {

  @Test
  @SuppressWarnings("unchecked")
  void resolvesAllergiesFromUserAllergiesTable() {
    UUID userId = UUID.randomUUID();
    UUID peanutId = UUID.randomUUID();

    User user = new User();
    user.setId(userId);
    user.setDisplayName("An");
    user.setTimezone("Asia/Ho_Chi_Minh");

    UserRepository users = mock(UserRepository.class);
    when(users.findById(userId)).thenReturn(Optional.of(user));

    UserAllergy allergy = new UserAllergy();
    allergy.setId(new UserAllergyId(userId, peanutId));
    UserAllergyRepository allergyRepo = mock(UserAllergyRepository.class);
    when(allergyRepo.findAllByIdUserId(userId)).thenReturn(List.of(allergy));

    IngredientLookup lookup = mock(IngredientLookup.class);
    when(lookup.findSummariesByIds(Set.of(peanutId)))
        .thenReturn(Map.of(peanutId, new IngredientSummary(peanutId, "PEANUT", "Peanuts", true)));

    ObjectProvider<ProfileBodyMetrics> bodyMetrics = mock(ObjectProvider.class);
    ObjectProvider<DailyProgressProvider> dailyProgress = mock(ObjectProvider.class);
    ObjectProvider<UserAllergyRepository> allergyProvider = mock(ObjectProvider.class);
    when(allergyProvider.getIfAvailable()).thenReturn(allergyRepo);
    ObjectProvider<IngredientLookup> lookupProvider = mock(ObjectProvider.class);
    when(lookupProvider.getIfAvailable()).thenReturn(lookup);

    HealthChatContextProvider provider = new HealthChatContextProvider(
        users,
        bodyMetrics,
        dailyProgress,
        allergyProvider,
        lookupProvider,
        Clock.fixed(Instant.parse("2026-11-26T00:00:00Z"), ZoneOffset.UTC));

    ChatContextData data = provider.get(userId);

    assertThat(data.allergies()).containsExactly("Peanuts");
    assertThat(data.allergyCodes()).containsExactly("PEANUT");
  }

  @Test
  @SuppressWarnings("unchecked")
  void resolvesFullProfileConditionsMacrosMealsAndWeightTrend() {
    UUID userId = UUID.randomUUID();
    UUID foodId = UUID.randomUUID();
    LocalDate today = LocalDate.of(2026, 11, 26);

    User user = new User();
    user.setId(userId);
    user.setDisplayName("Minh");
    user.setTimezone("Asia/Ho_Chi_Minh");
    UserRepository users = mock(UserRepository.class);
    when(users.findById(userId)).thenReturn(Optional.of(user));

    UserHealthProfile healthProfile = new UserHealthProfile();
    healthProfile.setUserId(userId);
    healthProfile.setGender(Gender.MALE);
    healthProfile.setDob(LocalDate.of(1996, 1, 10));
    healthProfile.setHeightCm(new BigDecimal("172"));
    healthProfile.setWeightKg(new BigDecimal("72.0"));
    healthProfile.setLatestLoggedWeightKg(new BigDecimal("70.5"));
    healthProfile.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE);
    healthProfile.setGoalType(GoalType.LOSE_WEIGHT);
    healthProfile.setTargetWeightKg(new BigDecimal("66.0"));
    healthProfile.setWeeklyRateKg(new BigDecimal("0.50"));
    healthProfile.setBmr(new BigDecimal("1660"));
    healthProfile.setTdee(new BigDecimal("2573"));
    healthProfile.setDailyCalorieGoal(new BigDecimal("2073"));
    healthProfile.setProteinGrams(new BigDecimal("141"));
    healthProfile.setCarbGrams(new BigDecimal("247"));
    healthProfile.setFatGrams(new BigDecimal("57"));
    healthProfile.setWaterLiters(new BigDecimal("2.47"));
    healthProfile.setStepGoal(8000);

    UserHealthProfileRepository profileRepo = mock(UserHealthProfileRepository.class);
    when(profileRepo.findById(userId)).thenReturn(Optional.of(healthProfile));

    ChronicCondition diabetes = new ChronicCondition();
    diabetes.setCode("DIABETES_T2");
    diabetes.setName("Tiểu đường tuýp 2");
    diabetes.setActive(true);
    UserChronicCondition ucc = new UserChronicCondition();
    ucc.setCondition(diabetes);
    UserChronicConditionRepository conditionRepo = mock(UserChronicConditionRepository.class);
    when(conditionRepo.findAllByUserIdWithCondition(userId)).thenReturn(List.of(ucc));

    DailySummary summary = new DailySummary();
    summary.setSummaryDate(today);
    summary.setMealsLoggedCount(1);
    summary.setCaloriesConsumed(480);
    summary.setTargetCalories(2073);
    summary.setProteinG(new BigDecimal("28.0"));
    summary.setCarbsG(new BigDecimal("55.0"));
    summary.setFatG(new BigDecimal("14.0"));
    summary.setSugarG(new BigDecimal("4.0"));
    summary.setFiberG(new BigDecimal("5.0"));
    summary.setWaterMl(1200);
    summary.setTargetWaterMl(2470);
    summary.setSteps(4500);
    summary.setCaloriesBurned(180);
    DailySummaryRepository summaryRepo = mock(DailySummaryRepository.class);
    when(summaryRepo.findByUserIdAndSummaryDate(userId, today)).thenReturn(Optional.of(summary));

    UserFoodLog foodLog = new UserFoodLog();
    foodLog.setFoodId(foodId);
    foodLog.setMealType(MealType.BREAKFAST);
    foodLog.setCalories(new BigDecimal("480"));
    UserFoodLogRepository logRepo = mock(UserFoodLogRepository.class);
    when(logRepo.findByUserIdAndLoggedDateAndDeletedAtIsNullOrderByLoggedAtAsc(userId, today))
        .thenReturn(List.of(foodLog));

    Food food = new Food();
    food.setId(foodId);
    food.setName("Phở bò");
    FoodRepository foodRepo = mock(FoodRepository.class);
    when(foodRepo.findAllById(List.of(foodId))).thenReturn(List.of(food));

    WeightLog w1 = new WeightLog();
    w1.setWeightKg(new BigDecimal("72.0"));
    w1.setLoggedAt(Instant.parse("2026-11-01T00:00:00Z"));
    WeightLog w2 = new WeightLog();
    w2.setWeightKg(new BigDecimal("70.5"));
    w2.setLoggedAt(Instant.parse("2026-11-25T00:00:00Z"));
    WeightLogRepository weightRepo = mock(WeightLogRepository.class);
    when(weightRepo.findByUserIdAndLoggedAtInRange(eq(userId), any(), any())).thenReturn(List.of(w1, w2));

    HealthChatContextProvider provider = new HealthChatContextProvider(
        users,
        providerOf(null),
        providerOf(null),
        providerOf(null),
        providerOf(null),
        providerOf(profileRepo),
        providerOf(new ProfileDomainService()),
        providerOf(conditionRepo),
        providerOf(summaryRepo),
        providerOf(logRepo),
        providerOf(foodRepo),
        providerOf(weightRepo),
        Clock.fixed(Instant.parse("2026-11-26T05:00:00Z"), ZoneOffset.UTC));

    ChatContextData data = provider.get(userId);

    assertThat(data.profile()).containsEntry("gender", "MALE")
        .containsEntry("age", 30)
        .containsEntry("goalType", "LOSE_WEIGHT")
        .containsKey("bmi")
        .containsKey("tdee")
        .containsKey("proteinGoalG");
    assertThat(data.profile()).doesNotContainKey("dob");
    assertThat(data.today()).containsEntry("consumedKcal", 480)
        .containsEntry("proteinG", 28);
    assertThat(data.chronicConditions()).extracting(ChatContextData.ConditionInfo::code)
        .containsExactly("DIABETES_T2");
    assertThat(data.todayMeals()).extracting(ChatContextData.TodayMealItem::foodName)
        .containsExactly("Phở bò");
    assertThat(data.recentWeight()).containsEntry("trend", "DOWN")
        .containsEntry("logCount", 2);
  }

  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> providerOf(T instance) {
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(instance);
    return provider;
  }
}
