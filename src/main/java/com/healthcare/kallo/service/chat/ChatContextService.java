package com.healthcare.kallo.service.chat;

import com.healthcare.kallo.model.act.ActivityLog;
import com.healthcare.kallo.model.act.WeightLog;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.food.Ingredient;
import com.healthcare.kallo.model.profile.UserAllergy;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.repository.act.ActivityLogRepository;
import com.healthcare.kallo.repository.act.WeightLogRepository;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.food.IngredientRepository;
import com.healthcare.kallo.repository.profile.UserAllergyRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.chat.context.ActivityContext;
import com.healthcare.kallo.service.chat.context.ChatContext;
import com.healthcare.kallo.service.chat.context.ContextMetadata;
import com.healthcare.kallo.service.chat.context.FoodPreferenceContext;
import com.healthcare.kallo.service.chat.context.LongTermHealthTrendContext;
import com.healthcare.kallo.service.chat.context.NutritionGoalsContext;
import com.healthcare.kallo.service.chat.context.NutritionHistoryContext;
import com.healthcare.kallo.service.chat.context.TodayNutritionContext;
import com.healthcare.kallo.service.chat.context.UserProfileContext;
import com.healthcare.kallo.service.chat.context.WeightTrendContext;
import com.healthcare.kallo.service.game.DailyProgress;
import com.healthcare.kallo.service.game.DailyProgressProvider;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatContextService {
  private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  private final UserRepository users;
  private final UserHealthProfileRepository profiles;
  private final UserAllergyRepository allergies;
  private final IngredientRepository ingredients;
  private final ActivityLogRepository activities;
  private final WeightLogRepository weights;
  private final DailyProgressProvider dailyProgress;
  private final Clock clock;

  public ChatContextService(UserRepository users, UserHealthProfileRepository profiles,
      UserAllergyRepository allergies, IngredientRepository ingredients, ActivityLogRepository activities,
      WeightLogRepository weights, DailyProgressProvider dailyProgress, Clock clock) {
    this.users = users;
    this.profiles = profiles;
    this.allergies = allergies;
    this.ingredients = ingredients;
    this.activities = activities;
    this.weights = weights;
    this.dailyProgress = dailyProgress;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public ChatContext get(UUID userId) {
    User user = users.findById(userId).orElse(null);
    if (user == null) return ChatContext.empty();

    ZoneId zone = zoneOf(user.getTimezone());
    Instant asOf = clock.instant();
    LocalDate today = asOf.atZone(zone).toLocalDate();
    UserHealthProfile profile = profiles.findById(userId).orElse(null);
    WeightTrendContext trend = weightTrend(userId, asOf);

    UserProfileContext userProfile = profile == null ? null : new UserProfileContext(
        age(profile.getDob(), today), profile.getGender(), profile.getHeightCm(),
        valueOr(profile.getLatestLoggedWeightKg(), profile.getWeightKg()), profile.getTargetWeightKg(),
        profile.getActivityLevel());

    NutritionGoalsContext goals = profile == null ? null : new NutritionGoalsContext(
        profile.getDailyCalorieGoal(), profile.getTdee(), profile.getProteinGrams(), profile.getCarbGrams(),
        profile.getFatGrams(), profile.getWaterLiters() == null ? null : profile.getWaterLiters().movePointRight(3));

    DailyProgress progress = dailyProgress.find(userId, today).orElse(null);
    TodayNutritionContext nutrition = todayNutrition(today, progress);
    ActivityContext activity = activity(userId, today, zone, progress);
    FoodPreferenceContext preferences = preferences(userId);
    NutritionHistoryContext sevenDay = history(userId, today.minusDays(6), today, profile, zone);
    LongTermHealthTrendContext thirtyDay = longTermTrend(userId, today.minusDays(29), today, profile, zone);

    return new ChatContext(userProfile, goals, nutrition, activity, preferences, trend, sevenDay, thirtyDay,
        new ContextMetadata("2.0", asOf, zone.getId()));
  }

  private TodayNutritionContext todayNutrition(LocalDate today, DailyProgress progress) {
    return new TodayNutritionContext(today,
        integer(progress == null ? null : progress.caloriesIn()),
        progress == null ? null : progress.proteinG(), progress == null ? null : progress.carbsG(),
        progress == null ? null : progress.fatG(), List.of());
  }

  private NutritionHistoryContext history(UUID userId, LocalDate from, LocalDate to,
      UserHealthProfile profile, ZoneId zone) {
    List<DailyProgress> rows = dailyProgress.findRange(userId, from, to);
    Map<LocalDate, BigDecimal> weightsByDate = weightByDate(userId, from, to, zone);
    List<NutritionHistoryContext.DailySummary> daily = rows.stream().map(row -> {
      BigDecimal weight = weightsByDate.get(row.localDate());
      boolean met = row.calorieGoal() != null && row.caloriesIn() != null
          && row.caloriesIn() >= row.calorieGoal() * 0.9
          && row.caloriesIn() <= row.calorieGoal() * 1.1;
      return new NutritionHistoryContext.DailySummary(row.localDate(), row.caloriesIn(), row.proteinG(),
          row.carbsG(), row.fatG(), weight, met);
    }).toList();
    return new NutritionHistoryContext(rows.size(), average(rows, DailyProgress::caloriesIn),
        average(rows, DailyProgress::proteinG), average(rows, DailyProgress::carbsG),
        average(rows, DailyProgress::fatG), average(weightsByDate.values()),
        daily.isEmpty() ? null : BigDecimal.valueOf(daily.stream().filter(NutritionHistoryContext.DailySummary::calorieGoalMet).count())
            .divide(BigDecimal.valueOf(daily.size()), 4, java.math.RoundingMode.HALF_UP), daily);
  }

  private LongTermHealthTrendContext longTermTrend(UUID userId, LocalDate from, LocalDate to,
      UserHealthProfile profile, ZoneId zone) {
    List<DailyProgress> rows = dailyProgress.findRange(userId, from, to);
    Map<LocalDate, BigDecimal> weights = weightByDate(userId, from, to, zone);
    BigDecimal start = weights.get(from);
    BigDecimal end = weights.get(to);
    if (start == null) start = weights.values().stream().findFirst().orElse(profile == null ? null : profile.getWeightKg());
    if (end == null) end = weights.values().stream().reduce((first, last) -> last).orElse(profile == null ? null : profile.getWeightKg());
    return new LongTermHealthTrendContext(start, end, start == null || end == null ? null : end.subtract(start),
        average(rows, DailyProgress::caloriesIn), average(rows, DailyProgress::proteinG),
        average(rows, DailyProgress::carbsG), average(rows, DailyProgress::fatG));
  }

  private Map<LocalDate, BigDecimal> weightByDate(UUID userId, LocalDate from, LocalDate to, ZoneId zone) {
    return weights.findByUserIdAndLoggedAtInRange(userId, from.atStartOfDay(zone).toInstant(),
            to.plusDays(1).atStartOfDay(zone).toInstant()).stream()
        .collect(Collectors.toMap(log -> log.getLoggedAt().atZone(zone).toLocalDate(), WeightLog::getWeightKg,
            (first, last) -> last));
  }

  private <T extends Number> BigDecimal average(List<DailyProgress> rows, Function<DailyProgress, T> value) {
    return average(rows.stream().map(value).filter(java.util.Objects::nonNull)
        .map(number -> BigDecimal.valueOf(number.doubleValue())).toList());
  }

  private BigDecimal average(java.util.Collection<BigDecimal> values) {
    if (values.isEmpty()) return null;
    return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
        .divide(BigDecimal.valueOf(values.size()), 2, java.math.RoundingMode.HALF_UP);
  }

  private ActivityContext activity(UUID userId, LocalDate today, ZoneId zone, DailyProgress progress) {
    Instant from = today.atStartOfDay(zone).toInstant();
    Instant to = today.plusDays(1).atStartOfDay(zone).toInstant();
    List<ActivityLog> rows = activities.findStartedInRange(userId, from, to);
    BigDecimal calories = rows.stream().map(ActivityLog::getCaloriesBurned)
        .filter(value -> value != null).reduce(BigDecimal.ZERO, BigDecimal::add);
    int minutes = rows.stream().mapToInt(ActivityLog::getDurationMin).sum();
    List<ActivityContext.Activity> mapped = rows.stream()
        .map(row -> new ActivityContext.Activity(
            row.getCustomName() == null ? row.getActivityType().getCode() : row.getCustomName(),
            row.getDurationMin(), row.getCaloriesBurned()))
        .toList();
    return new ActivityContext(progress == null ? null : progress.steps(), calories, minutes, mapped,
        progress == null ? null : progress.waterMl());
  }

  private FoodPreferenceContext preferences(UUID userId) {
    List<UserAllergy> rows = allergies.findAllByIdUserId(userId);
    if (rows.isEmpty()) return new FoodPreferenceContext(List.of(), List.of(), List.of(), null, List.of());
    Map<UUID, Ingredient> byId = ingredients.findAllByIds(rows.stream()
        .map(row -> row.getId().getIngredientId()).toList()).stream()
        .collect(Collectors.toMap(Ingredient::getId, Function.identity()));
    List<FoodPreferenceContext.Allergy> mapped = rows.stream()
        .map(row -> {
          Ingredient ingredient = byId.get(row.getId().getIngredientId());
          return new FoodPreferenceContext.Allergy(
              ingredient == null ? row.getId().getIngredientId().toString() : ingredient.getName(),
              null, row.getNote());
        })
        .toList();
    return new FoodPreferenceContext(mapped, List.of(), List.of(), null, List.of());
  }

  private WeightTrendContext weightTrend(UUID userId, Instant asOf) {
    BigDecimal current = weights.findLatestAtOrBefore(userId, asOf).map(WeightLog::getWeightKg).orElse(null);
    BigDecimal sevenDays = weights.findLatestAtOrBefore(userId, asOf.minusSeconds(7 * 24 * 60 * 60L))
        .map(WeightLog::getWeightKg).orElse(null);
    BigDecimal thirtyDays = weights.findLatestAtOrBefore(userId, asOf.minusSeconds(30 * 24 * 60 * 60L))
        .map(WeightLog::getWeightKg).orElse(null);
    BigDecimal weeklyChange = current == null || sevenDays == null ? null : current.subtract(sevenDays);
    return new WeightTrendContext(current, sevenDays, thirtyDays, weeklyChange);
  }

  private ZoneId zoneOf(String timezone) {
    try {
      return timezone == null ? DEFAULT_ZONE : ZoneId.of(timezone);
    } catch (DateTimeException exception) {
      return DEFAULT_ZONE;
    }
  }

  private Integer age(LocalDate dob, LocalDate today) {
    return dob == null ? null : Period.between(dob, today).getYears();
  }

  private BigDecimal valueOr(BigDecimal value, BigDecimal fallback) {
    return value == null ? fallback : value;
  }

  private BigDecimal integer(Integer value) {
    return value == null ? null : BigDecimal.valueOf(value);
  }
}
