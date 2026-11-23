package com.healthcare.kallo.service.nutri.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.request.nutri.CopyMealRequest;
import com.healthcare.kallo.dto.request.nutri.CreateFoodLogRequest;
import com.healthcare.kallo.dto.request.nutri.UpdateFoodLogRequest;
import com.healthcare.kallo.dto.response.food.FoodSearchItemResponse;
import com.healthcare.kallo.dto.response.nutri.DailyFoodLogResponse;
import com.healthcare.kallo.dto.response.nutri.FoodLogResponse;
import com.healthcare.kallo.event.sys.MealLoggedEvent;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.model.nutri.UserFoodLog;
import com.healthcare.kallo.model.nutri.enums.FoodLogSource;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.food.FoodRepository;
import com.healthcare.kallo.repository.nutri.UserFoodLogRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.nutri.FoodLogService;
import com.healthcare.kallo.service.report.DailySummaryService;
import com.healthcare.kallo.service.plan.engine.VectorSimilarityEngine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FoodLogServiceImpl implements FoodLogService {

  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

  private final UserFoodLogRepository userFoodLogRepository;
  private final FoodRepository foodRepository;
  private final UserRepository userRepository;
  private final UserHealthProfileRepository userHealthProfileRepository;
  private final DailySummaryService dailySummaryService;
  private final ApplicationEventPublisher eventPublisher;
  private final VectorSimilarityEngine vectorEngine;
  private final Clock clock;

  public FoodLogServiceImpl(
      UserFoodLogRepository userFoodLogRepository,
      FoodRepository foodRepository,
      UserRepository userRepository,
      UserHealthProfileRepository userHealthProfileRepository,
      DailySummaryService dailySummaryService,
      ApplicationEventPublisher eventPublisher,
      VectorSimilarityEngine vectorEngine,
      Clock clock) {
    this.userFoodLogRepository = userFoodLogRepository;
    this.foodRepository = foodRepository;
    this.userRepository = userRepository;
    this.userHealthProfileRepository = userHealthProfileRepository;
    this.dailySummaryService = dailySummaryService;
    this.eventPublisher = eventPublisher;
    this.vectorEngine = vectorEngine;
    this.clock = clock;
  }

  @Override
  @Transactional
  public FoodLogResponse logFood(UUID userId, CreateFoodLogRequest request) {
    // 1. Idempotency check
    Optional<UserFoodLog> existing = userFoodLogRepository.findByUserIdAndClientUuid(userId, request.clientUuid());
    List<String> userAllergies = getUserAllergies(userId);
    if (existing.isPresent()) {
      return toResponse(existing.get(), userAllergies);
    }

    User user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    Food food = foodRepository.findVisibleFoodById(request.foodId(), userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.FOOD_NOT_FOUND));

    // 2. Nutrition Snapshot calculation
    String servingName = request.servingName() != null && !request.servingName().isBlank()
        ? request.servingName() : food.getDefaultServingName();

    BigDecimal servingQuantity = request.servingQuantity() != null ? request.servingQuantity() : BigDecimal.ONE;

    BigDecimal servingGrams = request.servingGrams() != null
        ? request.servingGrams()
        : food.getDefaultServingGrams().multiply(servingQuantity).setScale(1, RoundingMode.HALF_UP);

    BigDecimal factor = servingGrams.divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP);

    BigDecimal calories = food.getCaloriesPer100g().multiply(factor).setScale(1, RoundingMode.HALF_UP);
    BigDecimal proteinG = food.getProteinPer100g().multiply(factor).setScale(1, RoundingMode.HALF_UP);
    BigDecimal carbsG = food.getCarbsPer100g().multiply(factor).setScale(1, RoundingMode.HALF_UP);
    BigDecimal fatG = food.getFatPer100g().multiply(factor).setScale(1, RoundingMode.HALF_UP);
    BigDecimal sugarG = (food.getSugarPer100g() != null ? food.getSugarPer100g() : BigDecimal.ZERO)
        .multiply(factor).setScale(1, RoundingMode.HALF_UP);
    BigDecimal fiberG = (food.getFiberPer100g() != null ? food.getFiberPer100g() : BigDecimal.ZERO)
        .multiply(factor).setScale(1, RoundingMode.HALF_UP);

    Instant loggedAt = request.loggedAt() != null ? request.loggedAt() : Instant.now(clock);

    UserFoodLog log = new UserFoodLog();
    log.setUser(user);
    log.setFood(food);
    log.setMealType(request.mealType());
    log.setServingName(servingName);
    log.setServingGrams(servingGrams);
    log.setServingQuantity(servingQuantity);
    log.setSource(request.source() != null ? request.source() : FoodLogSource.SEARCH);
    log.setLoggedDate(request.loggedDate());
    log.setLoggedAt(loggedAt);
    log.setClientUuid(request.clientUuid());
    log.setImageUrl(request.imageUrl());
    log.setCalories(calories);
    log.setProteinG(proteinG);
    log.setCarbsG(carbsG);
    log.setFatG(fatG);
    log.setSugarG(sugarG);
    log.setFiberG(fiberG);
    log.setAiPredictedClassId(request.aiPredictedClassId());

    UserFoodLog saved = userFoodLogRepository.save(log);

    // 3. Online Adaptive Learning for preference vector (skip unextracted zero vectors)
    if (food.getFeatureVector() != null && !vectorEngine.isZeroVector(food.getFeatureVector())) {
      userHealthProfileRepository.findByUserId(userId).ifPresent(profile -> {
        List<Double> updated = vectorEngine.updatePreferenceAdaptive(
            profile.getPreferenceVector(),
            food.getFeatureVector(),
            VectorSimilarityEngine.DEFAULT_LEARNING_RATE
        );
        profile.setPreferenceVector(updated);
        userHealthProfileRepository.save(profile);
      });
    }

    // 4. Publish event & recalculate
    eventPublisher.publishEvent(new MealLoggedEvent(userId, request.mealType().name(), loggedAt));
    dailySummaryService.recalculate(userId, request.loggedDate());

    return toResponse(saved, userAllergies);
  }

  @Override
  @Transactional
  public FoodLogResponse updateFoodLog(UUID userId, UUID logId, UpdateFoodLogRequest request) {
    UserFoodLog log = userFoodLogRepository.findByIdAndUserId(logId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.FOOD_LOG_NOT_FOUND));

    Food food = log.getFood();
    if (request.mealType() != null) {
      log.setMealType(request.mealType());
    }
    if (request.servingName() != null && !request.servingName().isBlank()) {
      log.setServingName(request.servingName());
    }

    boolean needRecalc = false;
    if (request.servingQuantity() != null) {
      log.setServingQuantity(request.servingQuantity());
      needRecalc = true;
    }
    if (request.servingGrams() != null) {
      log.setServingGrams(request.servingGrams());
      needRecalc = true;
    } else if (needRecalc) {
      log.setServingGrams(food.getDefaultServingGrams().multiply(log.getServingQuantity()).setScale(1, RoundingMode.HALF_UP));
    }

    if (needRecalc) {
      BigDecimal factor = log.getServingGrams().divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP);
      log.setCalories(food.getCaloriesPer100g().multiply(factor).setScale(1, RoundingMode.HALF_UP));
      log.setProteinG(food.getProteinPer100g().multiply(factor).setScale(1, RoundingMode.HALF_UP));
      log.setCarbsG(food.getCarbsPer100g().multiply(factor).setScale(1, RoundingMode.HALF_UP));
      log.setFatG(food.getFatPer100g().multiply(factor).setScale(1, RoundingMode.HALF_UP));
      log.setSugarG((food.getSugarPer100g() != null ? food.getSugarPer100g() : BigDecimal.ZERO)
          .multiply(factor).setScale(1, RoundingMode.HALF_UP));
      log.setFiberG((food.getFiberPer100g() != null ? food.getFiberPer100g() : BigDecimal.ZERO)
          .multiply(factor).setScale(1, RoundingMode.HALF_UP));
    }

    UserFoodLog updated = userFoodLogRepository.save(log);
    dailySummaryService.recalculate(userId, log.getLoggedDate());

    List<String> userAllergies = getUserAllergies(userId);
    return toResponse(updated, userAllergies);
  }

  @Override
  @Transactional
  public void deleteFoodLog(UUID userId, UUID logId) {
    UserFoodLog log = userFoodLogRepository.findByIdAndUserId(logId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.FOOD_LOG_NOT_FOUND));

    LocalDate date = log.getLoggedDate();
    userFoodLogRepository.delete(log);
    dailySummaryService.recalculate(userId, date);
  }

  @Override
  @Transactional(readOnly = true)
  public DailyFoodLogResponse getDailyFoodLogs(UUID userId, LocalDate date) {
    List<UserFoodLog> logs = userFoodLogRepository.findByUserIdAndLoggedDateOrderByLoggedAtAsc(userId, date);
    List<String> userAllergies = getUserAllergies(userId);

    Map<MealType, List<FoodLogResponse>> meals = new EnumMap<>(MealType.class);
    for (MealType mt : MealType.values()) {
      meals.put(mt, new ArrayList<>());
    }

    BigDecimal totalCalories = BigDecimal.ZERO;
    BigDecimal totalProteinG = BigDecimal.ZERO;
    BigDecimal totalCarbsG = BigDecimal.ZERO;
    BigDecimal totalFatG = BigDecimal.ZERO;
    BigDecimal totalSugarG = BigDecimal.ZERO;
    BigDecimal totalFiberG = BigDecimal.ZERO;

    for (UserFoodLog l : logs) {
      FoodLogResponse resp = toResponse(l, userAllergies);
      meals.get(l.getMealType()).add(resp);
      totalCalories = totalCalories.add(l.getCalories());
      totalProteinG = totalProteinG.add(l.getProteinG());
      totalCarbsG = totalCarbsG.add(l.getCarbsG());
      totalFatG = totalFatG.add(l.getFatG());
      totalSugarG = totalSugarG.add(l.getSugarG());
      totalFiberG = totalFiberG.add(l.getFiberG());
    }

    int targetCalories = userHealthProfileRepository.findByUserId(userId)
        .map(UserHealthProfile::getDailyCalorieGoal)
        .orElse(2000);

    return new DailyFoodLogResponse(
        date,
        targetCalories,
        totalCalories.setScale(1, RoundingMode.HALF_UP),
        totalProteinG.setScale(1, RoundingMode.HALF_UP),
        totalCarbsG.setScale(1, RoundingMode.HALF_UP),
        totalFatG.setScale(1, RoundingMode.HALF_UP),
        totalSugarG.setScale(1, RoundingMode.HALF_UP),
        totalFiberG.setScale(1, RoundingMode.HALF_UP),
        meals
    );
  }

  @Override
  @Transactional(readOnly = true)
  public FoodLogResponse getFoodLog(UUID userId, UUID logId) {
    UserFoodLog log = userFoodLogRepository.findByIdAndUserId(logId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.FOOD_LOG_NOT_FOUND));
    return toResponse(log, getUserAllergies(userId));
  }

  @Override
  @Transactional(readOnly = true)
  public List<FoodSearchItemResponse> getRecentFoods(UUID userId, int limit) {
    int capped = Math.min(Math.max(limit, 1), 50);
    List<Food> foods = userFoodLogRepository.findRecentFoods(userId, PageRequest.of(0, capped));
    return foods.stream().map(FoodSearchItemResponse::fromEntity).toList();
  }

  @Override
  @Transactional
  public void copyMeals(UUID userId, CopyMealRequest request) {
    List<UserFoodLog> fromLogs = userFoodLogRepository.findByUserIdAndLoggedDate(userId, request.fromDate());
    if (fromLogs.isEmpty()) {
      return;
    }

    User user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    Instant now = Instant.now(clock);

    for (UserFoodLog source : fromLogs) {
      if (request.mealType() != null && source.getMealType() != request.mealType()) {
        continue;
      }
      UserFoodLog copy = new UserFoodLog();
      copy.setUser(user);
      copy.setFood(source.getFood());
      copy.setMealType(source.getMealType());
      copy.setServingName(source.getServingName());
      copy.setServingGrams(source.getServingGrams());
      copy.setServingQuantity(source.getServingQuantity());
      copy.setSource(FoodLogSource.SEARCH);
      copy.setLoggedDate(request.toDate());
      copy.setLoggedAt(now);
      copy.setClientUuid(UUID.randomUUID());
      copy.setImageUrl(source.getImageUrl());
      copy.setCalories(source.getCalories());
      copy.setProteinG(source.getProteinG());
      copy.setCarbsG(source.getCarbsG());
      copy.setFatG(source.getFatG());
      copy.setSugarG(source.getSugarG());
      copy.setFiberG(source.getFiberG());

      userFoodLogRepository.save(copy);
    }

    dailySummaryService.recalculate(userId, request.toDate());
  }

  private List<String> getUserAllergies(UUID userId) {
    return userHealthProfileRepository.findByUserId(userId)
        .map(UserHealthProfile::getAllergies)
        .orElse(Collections.emptyList());
  }

  private FoodLogResponse toResponse(UserFoodLog log, List<String> userAllergies) {
    List<String> foodAllergies = log.getFood().getAllergens();
    List<String> warnings = new ArrayList<>();
    if (foodAllergies != null && userAllergies != null) {
      for (String fa : foodAllergies) {
        for (String ua : userAllergies) {
          if (fa.equalsIgnoreCase(ua)) {
            warnings.add(fa);
          }
        }
      }
    }

    return new FoodLogResponse(
        log.getId(),
        log.getFood().getId(),
        log.getFood().getName(),
        log.getMealType(),
        log.getServingName(),
        log.getServingGrams(),
        log.getServingQuantity(),
        log.getSource(),
        log.getLoggedDate(),
        log.getLoggedAt(),
        log.getClientUuid(),
        log.getImageUrl(),
        log.getCalories(),
        log.getProteinG(),
        log.getCarbsG(),
        log.getFatG(),
        log.getSugarG(),
        log.getFiberG(),
        !warnings.isEmpty(),
        warnings
    );
  }
}
