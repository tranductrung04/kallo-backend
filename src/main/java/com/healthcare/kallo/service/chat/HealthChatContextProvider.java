package com.healthcare.kallo.service.chat;

import com.healthcare.kallo.model.act.WeightLog;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.nutri.DailySummary;
import com.healthcare.kallo.model.nutri.UserFoodLog;
import com.healthcare.kallo.model.profile.UserChronicCondition;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.repository.act.WeightLogRepository;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.food.FoodRepository;
import com.healthcare.kallo.repository.nutri.DailySummaryRepository;
import com.healthcare.kallo.repository.nutri.UserFoodLogRepository;
import com.healthcare.kallo.repository.profile.UserAllergyRepository;
import com.healthcare.kallo.repository.profile.UserChronicConditionRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.act.ProfileBodyMetrics;
import com.healthcare.kallo.service.game.DailyProgress;
import com.healthcare.kallo.service.game.DailyProgressProvider;
import com.healthcare.kallo.service.profile.ProfileDomainService;
import com.healthcare.kallo.service.profile.port.IngredientLookup;
import com.healthcare.kallo.service.profile.port.IngredientSummary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HealthChatContextProvider implements ChatContextProvider {
  private static final int WEIGHT_LOOKBACK_DAYS = 30;
  private static final int MAX_TODAY_MEALS = 20;

  private final UserRepository users;
  private final UserAllergyRepository userAllergies;
  private final IngredientLookup ingredientLookup;
  private final ObjectProvider<ProfileBodyMetrics> bodyMetrics;
  private final ObjectProvider<DailyProgressProvider> dailyProgress;
  private final ObjectProvider<UserHealthProfileRepository> healthProfiles;
  private final ObjectProvider<ProfileDomainService> profileDomainService;
  private final ObjectProvider<UserChronicConditionRepository> chronicConditions;
  private final ObjectProvider<DailySummaryRepository> dailySummaries;
  private final ObjectProvider<UserFoodLogRepository> foodLogs;
  private final ObjectProvider<FoodRepository> foods;
  private final ObjectProvider<WeightLogRepository> weightLogs;
  private final Clock clock;

  public HealthChatContextProvider(
      UserRepository users,
      UserAllergyRepository userAllergies,
      IngredientLookup ingredientLookup,
      ObjectProvider<ProfileBodyMetrics> bodyMetrics,
      ObjectProvider<DailyProgressProvider> dailyProgress,
      Clock clock) {
    this(users, userAllergies, ingredientLookup, bodyMetrics, dailyProgress,
        null, null, null, null, null, null, null, clock);
  }

  @Autowired
  public HealthChatContextProvider(
      UserRepository users,
      UserAllergyRepository userAllergies,
      IngredientLookup ingredientLookup,
      ObjectProvider<ProfileBodyMetrics> bodyMetrics,
      ObjectProvider<DailyProgressProvider> dailyProgress,
      ObjectProvider<UserHealthProfileRepository> healthProfiles,
      ObjectProvider<ProfileDomainService> profileDomainService,
      ObjectProvider<UserChronicConditionRepository> chronicConditions,
      ObjectProvider<DailySummaryRepository> dailySummaries,
      ObjectProvider<UserFoodLogRepository> foodLogs,
      ObjectProvider<FoodRepository> foods,
      ObjectProvider<WeightLogRepository> weightLogs,
      Clock clock) {
    this.users = Objects.requireNonNull(users, "users must not be null");
    this.userAllergies = Objects.requireNonNull(userAllergies, "userAllergies must not be null");
    this.ingredientLookup = Objects.requireNonNull(ingredientLookup, "ingredientLookup must not be null");
    this.bodyMetrics = bodyMetrics;
    this.dailyProgress = dailyProgress;
    this.healthProfiles = healthProfiles;
    this.profileDomainService = profileDomainService;
    this.chronicConditions = chronicConditions;
    this.dailySummaries = dailySummaries;
    this.foodLogs = foodLogs;
    this.foods = foods;
    this.weightLogs = weightLogs;
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  @Override
  @Transactional(readOnly = true)
  public ChatContextData get(UUID userId) {
    User user = users.findById(userId).orElse(null);
    if (user == null) return ChatContextData.empty();
    ZoneId zone = zoneOrDefault(user.getTimezone());
    Instant now = Instant.now(clock);
    LocalDate today = LocalDate.now(clock.withZone(zone));

    UserHealthProfile healthProfile = findHealthProfile(userId);
    Map<String, Object> profile = buildProfile(userId, user, healthProfile, today);
    Map<String, Object> todaySummary = buildTodaySummary(userId, today, healthProfile);
    AllergyResolution allergyResolution = resolveAllergies(userId);
    List<ChatContextData.ConditionInfo> conditions = resolveChronicConditions(userId);
    Map<String, Object> recentWeight = resolveRecentWeight(userId, now);
    List<ChatContextData.TodayMealItem> todayMeals = resolveTodayMeals(userId, today);

    return new ChatContextData(
        profile,
        todaySummary,
        allergyResolution.names(),
        allergyResolution.codes(),
        conditions,
        recentWeight,
        todayMeals);
  }

  private UserHealthProfile findHealthProfile(UUID userId) {
    UserHealthProfileRepository repo = available(healthProfiles);
    return repo == null ? null : repo.findById(userId).orElse(null);
  }

  private Map<String, Object> buildProfile(
      UUID userId, User user, UserHealthProfile healthProfile, LocalDate today) {
    Map<String, Object> profile = new LinkedHashMap<>();
    profile.put("timezone", user.getTimezone());

    if (healthProfile != null) {
      ProfileDomainService domain = available(profileDomainService);
      profile.put("gender", healthProfile.getGender().name());
      if (healthProfile.getDob() != null) {
        int age = domain != null
            ? domain.calculateAge(healthProfile.getDob(), today)
            : java.time.Period.between(healthProfile.getDob(), today).getYears();
        profile.put("age", age);
      }
      BigDecimal effectiveWeight = healthProfile.getLatestLoggedWeightKg() != null
          ? healthProfile.getLatestLoggedWeightKg()
          : healthProfile.getWeightKg();
      put(profile, "heightCm", decimal(healthProfile.getHeightCm()));
      put(profile, "weightKg", decimal(effectiveWeight));
      if (effectiveWeight != null && healthProfile.getHeightCm() != null
          && healthProfile.getHeightCm().compareTo(BigDecimal.ZERO) > 0) {
        BigDecimal bmi = domain != null
            ? domain.calculateBmi(effectiveWeight, healthProfile.getHeightCm())
            : calculateBmiFallback(effectiveWeight, healthProfile.getHeightCm());
        put(profile, "bmi", decimal(bmi));
      }
      profile.put("activityLevel", healthProfile.getActivityLevel().name());
      profile.put("goalType", healthProfile.getGoalType().name());
      put(profile, "targetWeightKg", decimal(healthProfile.getTargetWeightKg()));
      put(profile, "weeklyRateKg", decimal(healthProfile.getWeeklyRateKg()));
      put(profile, "bmr", decimal(healthProfile.getBmr()));
      put(profile, "tdee", decimal(healthProfile.getTdee()));
      put(profile, "dailyCalorieGoal", decimal(healthProfile.getDailyCalorieGoal()));
      put(profile, "waterLiters", decimal(healthProfile.getWaterLiters()));
      put(profile, "stepGoal", healthProfile.getStepGoal());
      put(profile, "proteinGoalG", decimal(healthProfile.getProteinGrams()));
      put(profile, "carbGoalG", decimal(healthProfile.getCarbGrams()));
      put(profile, "fatGoalG", decimal(healthProfile.getFatGrams()));
      return profile;
    }

    ProfileBodyMetrics metrics = available(bodyMetrics);
    if (metrics != null) {
      metrics.heightCm(userId).ifPresent(value -> profile.put("heightCm", decimal(value)));
      metrics.weightKg(userId).ifPresent(value -> profile.put("weightKg", decimal(value)));
    }
    return profile;
  }

  private Map<String, Object> buildTodaySummary(
      UUID userId, LocalDate today, UserHealthProfile healthProfile) {
    Map<String, Object> todaySummary = new LinkedHashMap<>();
    DailySummaryRepository summaryRepo = available(dailySummaries);
    DailySummary summary = summaryRepo == null
        ? null
        : summaryRepo.findByUserIdAndSummaryDate(userId, today).orElse(null);

    if (summary != null) {
      todaySummary.put("date", summary.getSummaryDate().toString());
      todaySummary.put("mealsLogged", summary.getMealsLoggedCount());
      put(todaySummary, "consumedKcal", summary.getCaloriesConsumed());
      Integer calorieGoal = summary.getTargetCalories() != null
          ? summary.getTargetCalories()
          : (healthProfile != null && healthProfile.getDailyCalorieGoal() != null
              ? healthProfile.getDailyCalorieGoal().setScale(0, RoundingMode.HALF_UP).intValue()
              : null);
      put(todaySummary, "calorieGoal", calorieGoal);
      put(todaySummary, "proteinG", decimal(summary.getProteinG()));
      put(todaySummary, "carbsG", decimal(summary.getCarbsG()));
      put(todaySummary, "fatG", decimal(summary.getFatG()));
      put(todaySummary, "sugarG", decimal(summary.getSugarG()));
      put(todaySummary, "fiberG", decimal(summary.getFiberG()));
      todaySummary.put("waterMl", summary.getWaterMl() != null ? summary.getWaterMl() : 0);
      Integer waterGoalMl = summary.getTargetWaterMl() != null
          ? summary.getTargetWaterMl()
          : (healthProfile != null && healthProfile.getWaterLiters() != null
              ? healthProfile.getWaterLiters().multiply(BigDecimal.valueOf(1000)).intValue()
              : null);
      put(todaySummary, "waterGoalMl", waterGoalMl);
      todaySummary.put("steps", summary.getSteps() != null ? summary.getSteps() : 0);
      put(todaySummary, "caloriesBurned", summary.getCaloriesBurned());
      return todaySummary;
    }

    DailyProgressProvider progressProvider = available(dailyProgress);
    DailyProgress progress = progressProvider == null ? null : progressProvider.find(userId, today).orElse(null);
    if (progress != null) {
      todaySummary.put("date", progress.localDate().toString());
      todaySummary.put("mealsLogged", progress.mealsLogged());
      put(todaySummary, "consumedKcal", progress.caloriesIn());
      put(todaySummary, "calorieGoal", progress.calorieGoal());
      todaySummary.put("waterMl", progress.waterMl());
      put(todaySummary, "waterGoalMl", progress.waterGoalMl());
      todaySummary.put("steps", progress.steps());
      put(todaySummary, "caloriesBurned", progress.caloriesBurned());
    } else if (healthProfile != null) {
      todaySummary.put("date", today.toString());
      todaySummary.put("mealsLogged", 0);
      todaySummary.put("consumedKcal", 0);
      if (healthProfile.getDailyCalorieGoal() != null) {
        todaySummary.put("calorieGoal", healthProfile.getDailyCalorieGoal().setScale(0, RoundingMode.HALF_UP).intValue());
      }
      todaySummary.put("proteinG", 0);
      todaySummary.put("carbsG", 0);
      todaySummary.put("fatG", 0);
      todaySummary.put("sugarG", 0);
      todaySummary.put("fiberG", 0);
      todaySummary.put("waterMl", 0);
      if (healthProfile.getWaterLiters() != null) {
        todaySummary.put("waterGoalMl", healthProfile.getWaterLiters().multiply(BigDecimal.valueOf(1000)).intValue());
      }
      todaySummary.put("steps", 0);
      todaySummary.put("caloriesBurned", 0);
    }
    return todaySummary;
  }

  private AllergyResolution resolveAllergies(UUID userId) {
    Set<UUID> ingredientIds = userAllergies.findAllByIdUserId(userId).stream()
        .map(allergy -> allergy.getId().getIngredientId())
        .collect(Collectors.toSet());
    if (ingredientIds.isEmpty()) {
      return new AllergyResolution(List.of(), List.of());
    }
    Map<UUID, IngredientSummary> summaries = ingredientLookup.findSummariesByIds(ingredientIds);
    List<String> names = ingredientIds.stream()
        .map(summaries::get)
        .filter(Objects::nonNull)
        .map(IngredientSummary::name)
        .filter(Objects::nonNull)
        .sorted()
        .toList();
    List<String> codes = ingredientIds.stream()
        .map(summaries::get)
        .filter(Objects::nonNull)
        .map(IngredientSummary::code)
        .filter(code -> code != null && !code.isBlank())
        .distinct()
        .sorted()
        .toList();
    return new AllergyResolution(names, codes);
  }

  private List<ChatContextData.ConditionInfo> resolveChronicConditions(UUID userId) {
    UserChronicConditionRepository repo = available(chronicConditions);
    if (repo == null) return List.of();
    List<UserChronicCondition> rows = repo.findAllByUserIdWithCondition(userId);
    if (rows == null || rows.isEmpty()) return List.of();
    return rows.stream()
        .map(UserChronicCondition::getCondition)
        .filter(condition -> condition != null && condition.isActive())
        .map(condition -> new ChatContextData.ConditionInfo(condition.getCode(), condition.getName()))
        .toList();
  }

  private Map<String, Object> resolveRecentWeight(UUID userId, Instant now) {
    WeightLogRepository repo = available(weightLogs);
    if (repo == null) return Map.of();
    Instant from = now.minus(WEIGHT_LOOKBACK_DAYS, ChronoUnit.DAYS);
    List<WeightLog> logs = repo.findByUserIdAndLoggedAtInRange(userId, from, now.plusSeconds(1));
    if (logs == null || logs.isEmpty()) return Map.of();

    WeightLog earliest = logs.getFirst();
    WeightLog latest = logs.getLast();
    BigDecimal delta = latest.getWeightKg().subtract(earliest.getWeightKg()).setScale(2, RoundingMode.HALF_UP);
    String trend = delta.compareTo(new BigDecimal("0.20")) > 0
        ? "UP"
        : delta.compareTo(new BigDecimal("-0.20")) < 0 ? "DOWN" : "STABLE";

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("latestWeightKg", decimal(latest.getWeightKg()));
    result.put("latestLoggedAt", latest.getLoggedAt().toString());
    result.put("earliestWeightKg", decimal(earliest.getWeightKg()));
    result.put("delta30dKg", decimal(delta));
    result.put("trend", trend);
    result.put("logCount", logs.size());
    return result;
  }

  private List<ChatContextData.TodayMealItem> resolveTodayMeals(UUID userId, LocalDate today) {
    UserFoodLogRepository logRepo = available(foodLogs);
    if (logRepo == null) return List.of();
    List<UserFoodLog> logs = logRepo.findByUserIdAndLoggedDateAndDeletedAtIsNullOrderByLoggedAtAsc(userId, today);
    if (logs == null || logs.isEmpty()) return List.of();

    FoodRepository foodRepo = available(foods);
    List<UUID> foodIds = logs.stream().map(UserFoodLog::getFoodId).filter(Objects::nonNull).distinct().toList();
    Map<UUID, Food> foodMap = (foodRepo == null || foodIds.isEmpty())
        ? Collections.emptyMap()
        : foodRepo.findAllById(foodIds).stream().collect(Collectors.toMap(Food::getId, Function.identity()));

    List<ChatContextData.TodayMealItem> items = new ArrayList<>();
    for (UserFoodLog entry : logs) {
      if (items.size() >= MAX_TODAY_MEALS) break;
      Food food = entry.getFoodId() != null ? foodMap.get(entry.getFoodId()) : null;
      String name = food != null && food.getName() != null
          ? food.getName()
          : (entry.getServingName() != null ? entry.getServingName() : "Món đã ghi");
      int kcal = entry.getCalories() != null
          ? entry.getCalories().setScale(0, RoundingMode.HALF_UP).intValue()
          : 0;
      items.add(new ChatContextData.TodayMealItem(
          entry.getMealType() != null ? entry.getMealType().name() : "SNACK",
          entry.getFoodId(),
          name,
          kcal,
          entry.getProteinG(),
          entry.getCarbsG(),
          entry.getFatG(),
          entry.getSugarG()));
    }
    return items;
  }

  private <T> T available(ObjectProvider<T> provider) {
    return provider == null ? null : provider.getIfAvailable();
  }

  private BigDecimal calculateBmiFallback(BigDecimal weightKg, BigDecimal heightCm) {
    BigDecimal heightM = heightCm.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
    return weightKg.divide(heightM.multiply(heightM), 2, RoundingMode.HALF_UP);
  }

  private ZoneId zoneOrDefault(String timezone) {
    try {
      return ZoneId.of(timezone == null ? "Asia/Ho_Chi_Minh" : timezone);
    } catch (java.time.DateTimeException exception) {
      return ZoneId.of("Asia/Ho_Chi_Minh");
    }
  }

  private Object decimal(BigDecimal value) {
    if (value == null) return null;
    return value.stripTrailingZeros().scale() <= 0 ? value.intValue() : value;
  }

  private void put(Map<String, Object> target, String key, Object value) {
    if (value != null) target.put(key, value);
  }

  private record AllergyResolution(List<String> names, List<String> codes) {}
}
