package com.healthcare.kallo.service.nutri.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.request.nutri.CopyMealRequest;
import com.healthcare.kallo.dto.request.nutri.CreateFoodLogRequest;
import com.healthcare.kallo.dto.request.nutri.FoodFeedbackRequest;
import com.healthcare.kallo.dto.request.nutri.UpdateFoodLogRequest;
import com.healthcare.kallo.dto.response.food.FoodSearchItemResponse;
import com.healthcare.kallo.dto.response.nutri.AllergicIngredientWarning;
import com.healthcare.kallo.dto.response.nutri.CopyMealResponse;
import com.healthcare.kallo.dto.response.nutri.DailyFoodLogsResponse;
import com.healthcare.kallo.dto.response.nutri.FoodLogResponse;
import com.healthcare.kallo.event.sys.MealLoggedEvent;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.model.nutri.DailySummary;
import com.healthcare.kallo.model.nutri.UserFoodLog;
import com.healthcare.kallo.model.nutri.enums.LogSource;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.repository.food.FoodRepository;
import com.healthcare.kallo.repository.nutri.DailySummaryRepository;
import com.healthcare.kallo.repository.nutri.UserFoodLogRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.act.UserTimeResolver;
import com.healthcare.kallo.service.food.FoodImageStorageService;
import com.healthcare.kallo.service.nutri.DailySummaryService;
import com.healthcare.kallo.service.nutri.FoodLogService;
import com.healthcare.kallo.service.nutri.FoodPreferenceSyncPort;
import com.healthcare.kallo.service.nutri.NutriAllergyChecker;
import com.healthcare.kallo.service.nutri.NutriImageStorageService;
import com.healthcare.kallo.service.sys.sync.SyncApplyResult;
import com.healthcare.kallo.service.sys.sync.SyncForbiddenReferenceException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FoodLogServiceImpl implements FoodLogService {

  private static final Logger log = LoggerFactory.getLogger(FoodLogServiceImpl.class);
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final int VIETFOOD_EXCLUDED_CLASS_ID = 27;
  private static final int MAX_VIETFOOD_CLASS_ID = 67;
  private static final int MAX_RECENT_LIMIT = 50;

  private final UserFoodLogRepository userFoodLogRepository;
  private final FoodRepository foodRepository;
  private final DailySummaryRepository dailySummaryRepository;
  private final UserHealthProfileRepository profileRepository;
  private final DailySummaryService dailySummaryService;
  private final NutriAllergyChecker allergyChecker;
  private final NutriImageStorageService imageStorageService;
  private final FoodImageStorageService foodImageStorageService;
  private final UserTimeResolver userTimeResolver;
  private final ObjectProvider<FoodPreferenceSyncPort> foodPreferenceSyncPort;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public FoodLogServiceImpl(
      UserFoodLogRepository userFoodLogRepository,
      FoodRepository foodRepository,
      DailySummaryRepository dailySummaryRepository,
      UserHealthProfileRepository profileRepository,
      DailySummaryService dailySummaryService,
      NutriAllergyChecker allergyChecker,
      NutriImageStorageService imageStorageService,
      FoodImageStorageService foodImageStorageService,
      UserTimeResolver userTimeResolver,
      ObjectProvider<FoodPreferenceSyncPort> foodPreferenceSyncPort,
      ApplicationEventPublisher events,
      Clock clock) {
    this.userFoodLogRepository = userFoodLogRepository;
    this.foodRepository = foodRepository;
    this.dailySummaryRepository = dailySummaryRepository;
    this.profileRepository = profileRepository;
    this.dailySummaryService = dailySummaryService;
    this.allergyChecker = allergyChecker;
    this.imageStorageService = imageStorageService;
    this.foodImageStorageService = foodImageStorageService;
    this.userTimeResolver = userTimeResolver;
    this.foodPreferenceSyncPort = foodPreferenceSyncPort;
    this.events = events;
    this.clock = clock;
  }

  @Override
  @Transactional
  public CreateResult logFood(UUID userId, CreateFoodLogRequest request) {
    if (request == null || request.foodId() == null || request.mealType() == null || request.clientUuid() == null) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
    validateAiClassId(request.aiPredictedClassId());

    ZoneId zone = userTimeResolver.zoneOf(userId);
    Optional<UserFoodLog> existingOpt =
        userFoodLogRepository.findByUserIdAndClientUuid(userId, request.clientUuid());
    if (existingOpt.isPresent()) {
      UserFoodLog existing = existingOpt.get();
      if (existing.getDeletedAt() != null) {
        throw new BusinessException(ErrorCode.RECORD_DELETED);
      }
      Food food = findAccessibleFoodForRead(existing.getFoodId());
      Set<UUID> allergicIds = allergyChecker.loadUserAllergicIngredientIds(userId);
      return new CreateResult(toResponse(existing, food, allergicIds), false);
    }

    Food food = requireLoggableFood(userId, request.foodId(), false);
    Instant now = Instant.now(clock);
    LocalDate today = userTimeResolver.localDate(now, zone);
    Instant loggedAt = request.loggedAt() != null ? request.loggedAt() : now;
    LocalDate loggedDate =
        request.loggedDate() != null
            ? request.loggedDate()
            : userTimeResolver.localDate(loggedAt, zone);
    if (loggedDate.isAfter(today)) {
      throw new BusinessException(ErrorCode.FUTURE_DATE_NOT_ALLOWED);
    }

    ServingAndSnapshot snapshot = computeSnapshot(food, request.servingGrams(), request.servingQuantity());
    LogSource source = request.source() != null ? request.source() : LogSource.SEARCH;
    String movedImageKey = null;
    if (request.tempImageKey() != null && !request.tempImageKey().isBlank()) {
      movedImageKey = imageStorageService.moveTempToMeals(userId, request.tempImageKey());
    }

    int inserted =
        userFoodLogRepository.insertIfAbsent(
            UUID.randomUUID(),
            userId,
            food.getId(),
            request.mealType().name(),
            snapshot.servingName(),
            snapshot.servingGrams(),
            snapshot.servingQuantity(),
            source.name(),
            loggedDate,
            loggedAt,
            movedImageKey,
            snapshot.calories(),
            snapshot.proteinG(),
            snapshot.carbsG(),
            snapshot.fatG(),
            snapshot.sugarG(),
            snapshot.fiberG(),
            request.aiPredictedClassId(),
            request.clientUuid(),
            now,
            now);

    if (inserted == 0) {
      imageStorageService.deleteIfPresent(movedImageKey);
      UserFoodLog existing =
          userFoodLogRepository
              .findByUserIdAndClientUuid(userId, request.clientUuid())
              .orElseThrow(() -> new BusinessException(ErrorCode.FOOD_LOG_NOT_FOUND));
      if (existing.getDeletedAt() != null) {
        throw new BusinessException(ErrorCode.RECORD_DELETED);
      }
      Set<UUID> allergicIds = allergyChecker.loadUserAllergicIngredientIds(userId);
      return new CreateResult(toResponse(existing, food, allergicIds), false);
    }

    UserFoodLog saved =
        userFoodLogRepository
            .findByUserIdAndClientUuid(userId, request.clientUuid())
            .orElseThrow(() -> new BusinessException(ErrorCode.FOOD_LOG_NOT_FOUND));

    dailySummaryService.recalculate(userId, loggedDate);
    events.publishEvent(new MealLoggedEvent(userId, request.mealType().name(), loggedAt));
    notifyPreferenceSync(userId, food.getId(), source);

    Set<UUID> allergicIds = allergyChecker.loadUserAllergicIngredientIds(userId);
    return new CreateResult(toResponse(saved, food, allergicIds), true);
  }

  @Override
  @Transactional(readOnly = true)
  public DailyFoodLogsResponse getByDate(UUID userId, LocalDate date) {
    ZoneId zone = userTimeResolver.zoneOf(userId);
    LocalDate targetDate =
        date != null ? date : userTimeResolver.localDate(Instant.now(clock), zone);

    List<UserFoodLog> logs =
        userFoodLogRepository.findByUserIdAndLoggedDateAndDeletedAtIsNullOrderByLoggedAtAsc(
            userId, targetDate);

    Map<UUID, Food> foodById = loadFoodsByIds(logs.stream().map(UserFoodLog::getFoodId).toList());
    Set<UUID> allergicIds =
        logs.isEmpty() ? Collections.emptySet() : allergyChecker.loadUserAllergicIngredientIds(userId);

    Map<MealType, List<FoodLogResponse>> grouped = new EnumMap<>(MealType.class);
    for (MealType type : MealType.values()) {
      grouped.put(type, new ArrayList<>());
    }

    BigDecimal totalCalories = BigDecimal.ZERO;
    BigDecimal totalProtein = BigDecimal.ZERO;
    BigDecimal totalCarbs = BigDecimal.ZERO;
    BigDecimal totalFat = BigDecimal.ZERO;
    BigDecimal totalSugar = BigDecimal.ZERO;
    BigDecimal totalFiber = BigDecimal.ZERO;

    for (UserFoodLog entry : logs) {
      Food food = foodById.get(entry.getFoodId());
      grouped.get(entry.getMealType()).add(toResponse(entry, food, allergicIds));
      totalCalories = totalCalories.add(nvl(entry.getCalories()));
      totalProtein = totalProtein.add(nvl(entry.getProteinG()));
      totalCarbs = totalCarbs.add(nvl(entry.getCarbsG()));
      totalFat = totalFat.add(nvl(entry.getFatG()));
      totalSugar = totalSugar.add(nvl(entry.getSugarG()));
      totalFiber = totalFiber.add(nvl(entry.getFiberG()));
    }

    int totalCaloriesInt = totalCalories.setScale(0, RoundingMode.HALF_UP).intValue();
    Integer targetCalories = resolveTargetCalories(userId, targetDate);
    Integer remainingCalories =
        targetCalories != null ? targetCalories - totalCaloriesInt : null;
    int mealsLoggedCount =
        (int) grouped.values().stream().filter(list -> !list.isEmpty()).count();

    return new DailyFoodLogsResponse(
        targetDate,
        grouped,
        totalCaloriesInt,
        targetCalories,
        remainingCalories,
        scale1(totalProtein),
        scale1(totalCarbs),
        scale1(totalFat),
        scale1(totalSugar),
        scale1(totalFiber),
        mealsLoggedCount);
  }

  @Override
  @Transactional(readOnly = true)
  public FoodLogResponse getById(UUID userId, UUID id) {
    UserFoodLog entry =
        userFoodLogRepository
            .findByIdAndUserIdAndDeletedAtIsNull(id, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FOOD_LOG_NOT_FOUND));
    Food food = findAccessibleFoodForRead(entry.getFoodId());
    Set<UUID> allergicIds = allergyChecker.loadUserAllergicIngredientIds(userId);
    return toResponse(entry, food, allergicIds);
  }

  @Override
  @Transactional
  public FoodLogResponse update(UUID userId, UUID id, UpdateFoodLogRequest request) {
    userTimeResolver.zoneOf(userId);
    UserFoodLog entry =
        userFoodLogRepository
            .findByIdAndUserId(id, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FOOD_LOG_NOT_FOUND));
    if (entry.getDeletedAt() != null) {
      throw new BusinessException(ErrorCode.RECORD_DELETED);
    }

    Instant clientUpdatedAt = request.clientUpdatedAt();
    if (clientUpdatedAt != null && clientUpdatedAt.isBefore(entry.getUpdatedAt())) {
      Food currentFood = findAccessibleFoodForRead(entry.getFoodId());
      Set<UUID> allergicIds = allergyChecker.loadUserAllergicIngredientIds(userId);
      return toResponse(entry, currentFood, allergicIds);
    }

    Food food =
        entry.getFoodId() != null ? foodRepository.findById(entry.getFoodId()).orElse(null) : null;
    ServingAndSnapshot snapshot;
    if (food != null) {
      snapshot = computeSnapshot(food, request.servingGrams(), request.servingQuantity());
    } else {
      snapshot = scaleFromExisting(entry, request.servingGrams(), request.servingQuantity());
    }

    entry.setMealType(request.mealType());
    entry.setServingName(snapshot.servingName());
    entry.setServingGrams(snapshot.servingGrams());
    entry.setServingQuantity(snapshot.servingQuantity());
    entry.setCalories(snapshot.calories());
    entry.setProteinG(snapshot.proteinG());
    entry.setCarbsG(snapshot.carbsG());
    entry.setFatG(snapshot.fatG());
    entry.setSugarG(snapshot.sugarG());
    entry.setFiberG(snapshot.fiberG());
    entry.setUpdatedAt(Instant.now(clock));

    UserFoodLog saved = userFoodLogRepository.saveAndFlush(entry);
    dailySummaryService.recalculate(userId, saved.getLoggedDate());

    Set<UUID> allergicIds = allergyChecker.loadUserAllergicIngredientIds(userId);
    return toResponse(saved, food, allergicIds);
  }

  @Override
  @Transactional
  public void delete(UUID userId, UUID id) {
    userTimeResolver.zoneOf(userId);
    UserFoodLog entry =
        userFoodLogRepository
            .findByIdAndUserId(id, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FOOD_LOG_NOT_FOUND));
    if (entry.getDeletedAt() != null) {
      return;
    }

    Instant now = Instant.now(clock);
    entry.setDeletedAt(now);
    entry.setUpdatedAt(now);
    userFoodLogRepository.saveAndFlush(entry);

    if (entry.getLoggedDate() != null) {
      dailySummaryService.recalculate(userId, entry.getLoggedDate());
    }
  }

  @Override
  @Transactional
  public CopyMealResponse copyMeals(UUID userId, CopyMealRequest request) {
    ZoneId zone = userTimeResolver.zoneOf(userId);
    Instant now = Instant.now(clock);
    LocalDate today = userTimeResolver.localDate(now, zone);
    if (request.targetDate().isAfter(today.plusDays(1))) {
      throw new BusinessException(ErrorCode.FUTURE_DATE_NOT_ALLOWED);
    }

    List<UserFoodLog> sourceLogs =
        request.mealType() != null
            ? userFoodLogRepository
                .findByUserIdAndLoggedDateAndMealTypeAndDeletedAtIsNullOrderByLoggedAtAsc(
                    userId, request.sourceDate(), request.mealType())
            : userFoodLogRepository.findByUserIdAndLoggedDateAndDeletedAtIsNullOrderByLoggedAtAsc(
                userId, request.sourceDate());

    if (sourceLogs.isEmpty()) {
      return new CopyMealResponse(
          request.sourceDate(), request.targetDate(), 0, List.of(), List.of());
    }

    Map<UUID, Food> foodById = loadFoodsByIds(sourceLogs.stream().map(UserFoodLog::getFoodId).toList());
    Set<UUID> allergicIds = allergyChecker.loadUserAllergicIngredientIds(userId);

    List<FoodLogResponse> copiedLogs = new ArrayList<>();
    List<CopyMealResponse.SkippedFoodItem> skippedFoods = new ArrayList<>();
    Set<MealType> copiedMeals = new LinkedHashSet<>();

    for (UserFoodLog src : sourceLogs) {
      UUID foodId = src.getFoodId();
      Food food = foodId == null ? null : foodById.get(foodId);
      if (food == null || food.getDeletedAt() != null) {
        skippedFoods.add(new CopyMealResponse.SkippedFoodItem(foodId, "FOOD_ITEM_DELETED"));
        continue;
      }
      if (food.getOwnerUserId() != null && !food.getOwnerUserId().equals(userId)) {
        skippedFoods.add(new CopyMealResponse.SkippedFoodItem(foodId, "FORBIDDEN_FOOD_ACCESS"));
        continue;
      }

      ServingAndSnapshot snapshot =
          computeSnapshot(food, src.getServingGrams(), src.getServingQuantity());
      UUID clientUuid = UUID.randomUUID();
      userFoodLogRepository.insertIfAbsent(
          UUID.randomUUID(),
          userId,
          food.getId(),
          src.getMealType().name(),
          snapshot.servingName(),
          snapshot.servingGrams(),
          snapshot.servingQuantity(),
          LogSource.COPY.name(),
          request.targetDate(),
          now,
          null,
          snapshot.calories(),
          snapshot.proteinG(),
          snapshot.carbsG(),
          snapshot.fatG(),
          snapshot.sugarG(),
          snapshot.fiberG(),
          null,
          clientUuid,
          now,
          now);

      UserFoodLog insertedLog =
          userFoodLogRepository
              .findByUserIdAndClientUuid(userId, clientUuid)
              .orElseThrow(() -> new BusinessException(ErrorCode.FOOD_LOG_NOT_FOUND));
      copiedLogs.add(toResponse(insertedLog, food, allergicIds));
      copiedMeals.add(src.getMealType());
    }

    if (!copiedLogs.isEmpty()) {
      dailySummaryService.recalculate(userId, request.targetDate());
      for (MealType mealType : copiedMeals) {
        events.publishEvent(new MealLoggedEvent(userId, mealType.name(), now));
      }
    }

    return new CopyMealResponse(
        request.sourceDate(),
        request.targetDate(),
        copiedLogs.size(),
        copiedLogs,
        skippedFoods);
  }

  @Override
  @Transactional(readOnly = true)
  public List<FoodSearchItemResponse> getRecentFoods(UUID userId, int limit) {
    userTimeResolver.zoneOf(userId);
    int boundedLimit = Math.max(1, Math.min(limit, MAX_RECENT_LIMIT));
    List<UUID> foodIds = userFoodLogRepository.findRecentDistinctFoodIds(userId, boundedLimit);
    if (foodIds.isEmpty()) {
      return List.of();
    }
    Map<UUID, Food> foodById = loadFoodsByIds(foodIds);
    List<FoodSearchItemResponse> results = new ArrayList<>(foodIds.size());
    for (UUID foodId : foodIds) {
      Food food = foodById.get(foodId);
      if (food != null && food.getDeletedAt() == null) {
        results.add(toFoodSearchItem(food));
      }
    }
    return results;
  }

  @Override
  @Transactional
  public FoodLogResponse submitFeedback(UUID userId, UUID id, FoodFeedbackRequest request) {
    userTimeResolver.zoneOf(userId);
    UserFoodLog entry =
        userFoodLogRepository
            .findByIdAndUserId(id, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FOOD_LOG_NOT_FOUND));
    if (entry.getDeletedAt() != null) {
      throw new BusinessException(ErrorCode.RECORD_DELETED);
    }

    entry.setFeedbackGiven(true);
    entry.setUserConsentedFeedback(Boolean.TRUE.equals(request.consented()));
    entry.setUpdatedAt(Instant.now(clock));
    UserFoodLog saved = userFoodLogRepository.saveAndFlush(entry);

    Food food = findAccessibleFoodForRead(saved.getFoodId());
    Set<UUID> allergicIds = allergyChecker.loadUserAllergicIngredientIds(userId);
    return toResponse(saved, food, allergicIds);
  }

  @Override
  @Transactional
  public List<FoodLogResponse> applyMealPlanItems(UUID userId, List<MealPlanApplyItem> items) {
    if (items == null || items.isEmpty()) {
      return List.of();
    }
    ZoneId zone = userTimeResolver.zoneOf(userId);
    Instant now = Instant.now(clock);
    Set<UUID> allergicIds = allergyChecker.loadUserAllergicIngredientIds(userId);
    List<FoodLogResponse> responses = new ArrayList<>(items.size());
    Set<LocalDate> affectedDates = new LinkedHashSet<>();

    for (MealPlanApplyItem item : items) {
      Food food = requireLoggableFood(userId, item.foodId(), false);
      Instant loggedAt = item.loggedAt() != null ? item.loggedAt() : now;
      LocalDate loggedDate =
          item.loggedDate() != null
              ? item.loggedDate()
              : userTimeResolver.localDate(loggedAt, zone);
      UUID clientUuid = item.clientUuid() != null ? item.clientUuid() : UUID.randomUUID();
      ServingAndSnapshot snapshot =
          computeSnapshot(food, item.servingGrams(), item.servingQuantity());

      int inserted =
          userFoodLogRepository.insertIfAbsent(
              UUID.randomUUID(),
              userId,
              food.getId(),
              item.mealType().name(),
              snapshot.servingName(),
              snapshot.servingGrams(),
              snapshot.servingQuantity(),
              LogSource.MEAL_PLAN.name(),
              loggedDate,
              loggedAt,
              null,
              snapshot.calories(),
              snapshot.proteinG(),
              snapshot.carbsG(),
              snapshot.fatG(),
              snapshot.sugarG(),
              snapshot.fiberG(),
              null,
              clientUuid,
              now,
              now);

      UserFoodLog logRow =
          userFoodLogRepository
              .findByUserIdAndClientUuid(userId, clientUuid)
              .orElseThrow(() -> new BusinessException(ErrorCode.FOOD_LOG_NOT_FOUND));
      if (inserted == 1) {
        affectedDates.add(loggedDate);
        events.publishEvent(new MealLoggedEvent(userId, item.mealType().name(), loggedAt));
        notifyPreferenceSync(userId, food.getId(), LogSource.MEAL_PLAN);
      }
      responses.add(toResponse(logRow, food, allergicIds));
    }

    for (LocalDate date : affectedDates) {
      dailySummaryService.recalculate(userId, date);
    }
    return responses;
  }

  @Override
  @Transactional
  public SyncApplyResult upsertFromSync(
      UUID userId, UUID clientUuid, Instant opUpdatedAt, Map<String, Object> data) {
    if (data == null) {
      throw new IllegalArgumentException("FOOD_LOG upsert requires data payload");
    }

    Instant normalizedUpdatedAt = opUpdatedAt.truncatedTo(ChronoUnit.MICROS);
    ZoneId zone = userTimeResolver.zoneOf(userId);
    UUID foodId = parseUuidRequired(data, "foodId", "food_id");
    MealType mealType = parseEnumRequired(MealType.class, data, "mealType", "meal_type");
    LogSource source = parseEnumOptional(LogSource.class, LogSource.SEARCH, data, "source");
    BigDecimal requestedGrams = parseDecimalOptional(data, "servingGrams", "serving_grams");
    BigDecimal requestedQty =
        parseDecimalOptional(data, "servingQuantity", "serving_quantity");
    if (requestedQty == null) {
      requestedQty = BigDecimal.ONE;
    }
    Instant loggedAt = parseInstantOptional(data, "loggedAt", "logged_at");
    LocalDate loggedDate = parseDateOptional(data, "loggedDate", "logged_date");
    if (loggedAt == null && loggedDate == null) {
      throw new IllegalArgumentException("FOOD_LOG upsert requires loggedAt or loggedDate");
    }
    if (loggedAt == null) {
      loggedAt = userTimeResolver.dayRange(loggedDate, zone).start();
    }
    Instant normalizedLoggedAt = loggedAt.truncatedTo(ChronoUnit.MICROS);
    if (loggedDate == null) {
      loggedDate = userTimeResolver.localDate(normalizedLoggedAt, zone);
    }
    Integer aiClassId = parseIntegerOptional(data, "aiPredictedClassId", "ai_predicted_class_id");
    validateAiClassId(aiClassId);

    Optional<UserFoodLog> existingOpt =
        userFoodLogRepository.findByUserIdAndClientUuid(userId, clientUuid);
    Food food = requireLoggableFood(userId, foodId, true);
    ServingAndSnapshot snapshot = computeSnapshot(food, requestedGrams, requestedQty);

    if (existingOpt.isEmpty()) {
      if (food.getDeletedAt() != null) {
        throw new BusinessException(ErrorCode.FOOD_ITEM_DELETED);
      }
      Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
      userFoodLogRepository.insertIfAbsent(
          UUID.randomUUID(),
          userId,
          food.getId(),
          mealType.name(),
          snapshot.servingName(),
          snapshot.servingGrams(),
          snapshot.servingQuantity(),
          source.name(),
          loggedDate,
          normalizedLoggedAt,
          null,
          snapshot.calories(),
          snapshot.proteinG(),
          snapshot.carbsG(),
          snapshot.fatG(),
          snapshot.sugarG(),
          snapshot.fiberG(),
          aiClassId,
          clientUuid,
          now,
          normalizedUpdatedAt);

      dailySummaryService.recalculate(userId, loggedDate);
      events.publishEvent(new MealLoggedEvent(userId, mealType.name(), normalizedLoggedAt));
      notifyPreferenceSync(userId, food.getId(), source);
      return SyncApplyResult.applied();
    }

    UserFoodLog existing = existingOpt.get();
    Instant existingUpdatedAt = existing.getUpdatedAt().truncatedTo(ChronoUnit.MICROS);
    if (existingUpdatedAt.isAfter(normalizedUpdatedAt)) {
      return SyncApplyResult.conflictServerWins(toSyncData(existing), existing.getUpdatedAt());
    }
    if (existingUpdatedAt.equals(normalizedUpdatedAt)) {
      if (existing.getDeletedAt() == null
          && syncPayloadMatches(
              existing, food.getId(), mealType, snapshot, loggedDate, normalizedLoggedAt)) {
        return SyncApplyResult.unchanged();
      }
      return SyncApplyResult.conflictServerWins(toSyncData(existing), existing.getUpdatedAt());
    }

    if (existing.getDeletedAt() != null && food.getDeletedAt() != null) {
      throw new BusinessException(ErrorCode.FOOD_ITEM_DELETED);
    }

    LocalDate previousDate = existing.getLoggedDate();
    existing.setFoodId(food.getId());
    existing.setMealType(mealType);
    existing.setServingName(snapshot.servingName());
    existing.setServingGrams(snapshot.servingGrams());
    existing.setServingQuantity(snapshot.servingQuantity());
    existing.setSource(source);
    existing.setLoggedDate(loggedDate);
    existing.setLoggedAt(normalizedLoggedAt);
    existing.setCalories(snapshot.calories());
    existing.setProteinG(snapshot.proteinG());
    existing.setCarbsG(snapshot.carbsG());
    existing.setFatG(snapshot.fatG());
    existing.setSugarG(snapshot.sugarG());
    existing.setFiberG(snapshot.fiberG());
    existing.setAiPredictedClassId(aiClassId);
    existing.setDeletedAt(null);
    existing.setUpdatedAt(normalizedUpdatedAt);
    userFoodLogRepository.saveAndFlush(existing);

    dailySummaryService.recalculate(userId, loggedDate);
    if (previousDate != null && !previousDate.equals(loggedDate)) {
      dailySummaryService.recalculate(userId, previousDate);
    }
    events.publishEvent(new MealLoggedEvent(userId, mealType.name(), normalizedLoggedAt));
    return SyncApplyResult.applied();
  }

  @Override
  @Transactional
  public SyncApplyResult softDeleteFromSync(UUID userId, UUID clientUuid, Instant opUpdatedAt) {
    userTimeResolver.zoneOf(userId);
    Instant normalizedUpdatedAt = opUpdatedAt.truncatedTo(ChronoUnit.MICROS);
    Optional<UserFoodLog> existingOpt =
        userFoodLogRepository.findByUserIdAndClientUuid(userId, clientUuid);
    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);

    if (existingOpt.isEmpty()) {
      userFoodLogRepository.insertTombstoneIfAbsent(
          UUID.randomUUID(), userId, clientUuid, normalizedUpdatedAt, now);
      return SyncApplyResult.applied();
    }

    UserFoodLog existing = existingOpt.get();
    if (existing.getUpdatedAt().truncatedTo(ChronoUnit.MICROS).isAfter(normalizedUpdatedAt)) {
      return SyncApplyResult.conflictServerWins(toSyncData(existing), existing.getUpdatedAt());
    }
    if (existing.getDeletedAt() != null) {
      return SyncApplyResult.unchanged();
    }

    LocalDate affectedDate = existing.getLoggedDate();
    existing.setDeletedAt(now);
    existing.setUpdatedAt(normalizedUpdatedAt);
    userFoodLogRepository.saveAndFlush(existing);
    if (affectedDate != null) {
      dailySummaryService.recalculate(userId, affectedDate);
    }
    return SyncApplyResult.applied();
  }

  private Food requireLoggableFood(UUID userId, UUID foodId, boolean forSync) {
    Optional<Food> withIngredients = foodRepository.findByIdWithIngredients(foodId);
    Food food = withIngredients.orElseGet(() -> foodRepository.findById(foodId).orElse(null));
    if (food == null) {
      throw new BusinessException(ErrorCode.FOOD_NOT_FOUND);
    }
    if (food.getOwnerUserId() != null && !food.getOwnerUserId().equals(userId)) {
      if (forSync) {
        throw new SyncForbiddenReferenceException("Forbidden access to custom food " + foodId);
      }
      throw new BusinessException(ErrorCode.FORBIDDEN_FOOD_ACCESS);
    }
    if (!forSync && food.getDeletedAt() != null) {
      throw new BusinessException(ErrorCode.FOOD_ITEM_DELETED);
    }
    return food;
  }

  private Food findAccessibleFoodForRead(UUID foodId) {
    if (foodId == null) {
      return null;
    }
    return foodRepository
        .findByIdWithIngredients(foodId)
        .orElseGet(() -> foodRepository.findById(foodId).orElse(null));
  }

  private Map<UUID, Food> loadFoodsByIds(List<UUID> ids) {
    List<UUID> distinctIds = ids.stream().filter(Objects::nonNull).distinct().toList();
    if (distinctIds.isEmpty()) {
      return Collections.emptyMap();
    }
    return foodRepository.findAllById(distinctIds).stream()
        .collect(Collectors.toMap(Food::getId, Function.identity()));
  }

  private ServingAndSnapshot computeSnapshot(
      Food food, BigDecimal requestedGrams, BigDecimal requestedQuantity) {
    BigDecimal servingQuantity =
        requestedQuantity != null ? requestedQuantity : BigDecimal.ONE;
    BigDecimal servingGrams =
        requestedGrams != null ? requestedGrams : food.getDefaultServingGrams();
    if (servingQuantity.compareTo(BigDecimal.ZERO) <= 0
        || servingGrams == null
        || servingGrams.compareTo(BigDecimal.ZERO) <= 0) {
      throw new BusinessException(ErrorCode.INVALID_SERVING_AMOUNT);
    }

    BigDecimal normalizedGrams = scale1(servingGrams);
    BigDecimal normalizedQty = servingQuantity.setScale(2, RoundingMode.HALF_UP);
    BigDecimal ratio =
        normalizedGrams.multiply(normalizedQty).divide(HUNDRED, 6, RoundingMode.HALF_UP);

    String servingName =
        food.getDefaultServingName() != null && !food.getDefaultServingName().isBlank()
            ? food.getDefaultServingName()
            : "Serving";

    return new ServingAndSnapshot(
        servingName,
        normalizedGrams,
        normalizedQty,
        scale1(nvl(food.getCaloriesPer100g()).multiply(ratio)),
        scale1(nvl(food.getProteinPer100g()).multiply(ratio)),
        scale1(nvl(food.getCarbsPer100g()).multiply(ratio)),
        scale1(nvl(food.getFatPer100g()).multiply(ratio)),
        scale1(nvl(food.getSugarPer100g()).multiply(ratio)),
        scale1(nvl(food.getFiberPer100g()).multiply(ratio)));
  }

  private ServingAndSnapshot scaleFromExisting(
      UserFoodLog entry, BigDecimal requestedGrams, BigDecimal requestedQuantity) {
    BigDecimal newQty = requestedQuantity != null ? requestedQuantity : entry.getServingQuantity();
    BigDecimal newGrams = requestedGrams != null ? requestedGrams : entry.getServingGrams();
    if (newQty == null
        || newQty.compareTo(BigDecimal.ZERO) <= 0
        || newGrams == null
        || newGrams.compareTo(BigDecimal.ZERO) <= 0) {
      throw new BusinessException(ErrorCode.INVALID_SERVING_AMOUNT);
    }
    BigDecimal oldTotal = nvl(entry.getServingGrams()).multiply(nvl(entry.getServingQuantity()));
    BigDecimal newTotal = newGrams.multiply(newQty);
    BigDecimal factor =
        oldTotal.compareTo(BigDecimal.ZERO) > 0
            ? newTotal.divide(oldTotal, 6, RoundingMode.HALF_UP)
            : BigDecimal.ONE;

    return new ServingAndSnapshot(
        entry.getServingName(),
        scale1(newGrams),
        newQty.setScale(2, RoundingMode.HALF_UP),
        scale1(nvl(entry.getCalories()).multiply(factor)),
        scale1(nvl(entry.getProteinG()).multiply(factor)),
        scale1(nvl(entry.getCarbsG()).multiply(factor)),
        scale1(nvl(entry.getFatG()).multiply(factor)),
        scale1(nvl(entry.getSugarG()).multiply(factor)),
        scale1(nvl(entry.getFiberG()).multiply(factor)));
  }

  private Integer resolveTargetCalories(UUID userId, LocalDate date) {
    Optional<DailySummary> summaryOpt =
        dailySummaryRepository.findByUserIdAndSummaryDate(userId, date);
    if (summaryOpt.isPresent()) {
      return summaryOpt.get().getTargetCalories();
    }
    UserHealthProfile profile = profileRepository.findById(userId).orElse(null);
    if (profile == null
        || profile.getDailyCalorieGoal() == null
        || profile.getDailyCalorieGoal().compareTo(BigDecimal.ZERO) <= 0) {
      return null;
    }
    return profile.getDailyCalorieGoal().setScale(0, RoundingMode.HALF_UP).intValue();
  }

  private void notifyPreferenceSync(UUID userId, UUID foodId, LogSource source) {
    foodPreferenceSyncPort.ifAvailable(
        port -> {
          try {
            port.onFoodLogged(userId, foodId, source);
          } catch (RuntimeException e) {
            log.warn("FoodPreferenceSyncPort callback failed for user {}", userId, e);
          }
        });
  }

  private void validateAiClassId(Integer aiClassId) {
    if (aiClassId == null) {
      return;
    }
    if (aiClassId < 0 || aiClassId > MAX_VIETFOOD_CLASS_ID || aiClassId == VIETFOOD_EXCLUDED_CLASS_ID) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
  }

  private FoodLogResponse toResponse(
      UserFoodLog entry, Food food, Set<UUID> userAllergicIngredientIds) {
    List<AllergicIngredientWarning> warnings =
        allergyChecker.checkFoodAllergies(food, userAllergicIngredientIds);
    return new FoodLogResponse(
        entry.getId(),
        entry.getFoodId(),
        food != null ? food.getName() : null,
        entry.getMealType(),
        entry.getServingName(),
        entry.getServingGrams(),
        entry.getServingQuantity(),
        entry.getSource(),
        entry.getLoggedDate(),
        entry.getLoggedAt(),
        imageStorageService.presignedUrlOrNull(entry.getImageKey()),
        entry.getCalories(),
        entry.getProteinG(),
        entry.getCarbsG(),
        entry.getFatG(),
        entry.getSugarG(),
        entry.getFiberG(),
        entry.getAiPredictedClassId(),
        entry.isFeedbackGiven(),
        entry.getClientUuid(),
        !warnings.isEmpty(),
        warnings,
        entry.getCreatedAt(),
        entry.getUpdatedAt());
  }

  private FoodSearchItemResponse toFoodSearchItem(Food food) {
    return new FoodSearchItemResponse(
        food.getId(),
        food.getName(),
        food.getCategory(),
        food.getDefaultServingName(),
        food.getDefaultServingGrams(),
        food.getCaloriesPerServing(),
        food.getProteinPerServing(),
        food.getCarbsPerServing(),
        food.getFatPerServing(),
        foodImageStorageService.resolveImageUrl(food.getImageUrl()),
        food.getSource(),
        food.isVerified());
  }

  private Map<String, Object> toSyncData(UserFoodLog entry) {
    Map<String, Object> map = new LinkedHashMap<>();
    if (entry.getFoodId() != null) map.put("foodId", entry.getFoodId().toString());
    if (entry.getMealType() != null) map.put("mealType", entry.getMealType().name());
    if (entry.getServingName() != null) map.put("servingName", entry.getServingName());
    if (entry.getServingGrams() != null) map.put("servingGrams", entry.getServingGrams());
    if (entry.getServingQuantity() != null) map.put("servingQuantity", entry.getServingQuantity());
    if (entry.getSource() != null) map.put("source", entry.getSource().name());
    if (entry.getLoggedDate() != null) map.put("loggedDate", entry.getLoggedDate().toString());
    if (entry.getLoggedAt() != null) map.put("loggedAt", entry.getLoggedAt().toString());
    if (entry.getCalories() != null) map.put("calories", entry.getCalories());
    if (entry.getProteinG() != null) map.put("proteinG", entry.getProteinG());
    if (entry.getCarbsG() != null) map.put("carbsG", entry.getCarbsG());
    if (entry.getFatG() != null) map.put("fatG", entry.getFatG());
    if (entry.getSugarG() != null) map.put("sugarG", entry.getSugarG());
    if (entry.getFiberG() != null) map.put("fiberG", entry.getFiberG());
    if (entry.getDeletedAt() != null) map.put("deletedAt", entry.getDeletedAt().toString());
    return map;
  }

  private boolean syncPayloadMatches(
      UserFoodLog existing,
      UUID foodId,
      MealType mealType,
      ServingAndSnapshot snapshot,
      LocalDate loggedDate,
      Instant loggedAt) {
    Instant existingLoggedAt =
        existing.getLoggedAt() != null
            ? existing.getLoggedAt().truncatedTo(ChronoUnit.MICROS)
            : null;
    return Objects.equals(existing.getFoodId(), foodId)
        && existing.getMealType() == mealType
        && existing.getServingGrams() != null
        && existing.getServingGrams().compareTo(snapshot.servingGrams()) == 0
        && existing.getServingQuantity() != null
        && existing.getServingQuantity().compareTo(snapshot.servingQuantity()) == 0
        && Objects.equals(existing.getLoggedDate(), loggedDate)
        && Objects.equals(existingLoggedAt, loggedAt);
  }

  private static Object rawValue(Map<String, Object> data, String... keys) {
    for (String key : keys) {
      if (data.containsKey(key) && data.get(key) != null) {
        return data.get(key);
      }
    }
    return null;
  }

  private static UUID parseUuidRequired(Map<String, Object> data, String... keys) {
    Object val = rawValue(data, keys);
    if (val == null) {
      throw new IllegalArgumentException("Missing required UUID field " + keys[0]);
    }
    return val instanceof UUID uuid ? uuid : UUID.fromString(val.toString());
  }

  private static <E extends Enum<E>> E parseEnumRequired(
      Class<E> enumType, Map<String, Object> data, String... keys) {
    Object val = rawValue(data, keys);
    if (val == null) {
      throw new IllegalArgumentException("Missing required enum field " + keys[0]);
    }
    return Enum.valueOf(enumType, val.toString());
  }

  private static <E extends Enum<E>> E parseEnumOptional(
      Class<E> enumType, E defaultVal, Map<String, Object> data, String... keys) {
    Object val = rawValue(data, keys);
    return val == null ? defaultVal : Enum.valueOf(enumType, val.toString());
  }

  private static BigDecimal parseDecimalOptional(Map<String, Object> data, String... keys) {
    Object val = rawValue(data, keys);
    if (val == null) return null;
    return val instanceof BigDecimal bd ? bd : new BigDecimal(val.toString());
  }

  private static Integer parseIntegerOptional(Map<String, Object> data, String... keys) {
    Object val = rawValue(data, keys);
    if (val == null) return null;
    if (val instanceof Number n) return n.intValue();
    return Integer.parseInt(val.toString());
  }

  private static Instant parseInstantOptional(Map<String, Object> data, String... keys) {
    Object val = rawValue(data, keys);
    if (val == null) return null;
    return val instanceof Instant instant ? instant : Instant.parse(val.toString());
  }

  private static LocalDate parseDateOptional(Map<String, Object> data, String... keys) {
    Object val = rawValue(data, keys);
    if (val == null) return null;
    return val instanceof LocalDate ld ? ld : LocalDate.parse(val.toString());
  }

  private static BigDecimal nvl(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }

  private static BigDecimal scale1(BigDecimal value) {
    return nvl(value).setScale(1, RoundingMode.HALF_UP);
  }

  private record ServingAndSnapshot(
      String servingName,
      BigDecimal servingGrams,
      BigDecimal servingQuantity,
      BigDecimal calories,
      BigDecimal proteinG,
      BigDecimal carbsG,
      BigDecimal fatG,
      BigDecimal sugarG,
      BigDecimal fiberG) {}
}
