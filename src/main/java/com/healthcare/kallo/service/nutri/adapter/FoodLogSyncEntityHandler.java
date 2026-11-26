package com.healthcare.kallo.service.nutri.adapter;

import com.healthcare.kallo.model.nutri.UserFoodLog;
import com.healthcare.kallo.repository.nutri.UserFoodLogRepository;
import com.healthcare.kallo.service.nutri.FoodLogService;
import com.healthcare.kallo.service.sys.sync.SyncApplyResult;
import com.healthcare.kallo.service.sys.sync.SyncChange;
import com.healthcare.kallo.service.sys.sync.SyncEntityHandler;
import com.healthcare.kallo.service.sys.sync.SyncEntityType;
import com.healthcare.kallo.service.sys.sync.SyncOperation;
import com.healthcare.kallo.service.sys.sync.SyncOperationType;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** Offline sync handler delegating FOOD_LOG mutations through FoodLogService. */
@Component
public class FoodLogSyncEntityHandler implements SyncEntityHandler {

  private final FoodLogService foodLogService;
  private final UserFoodLogRepository userFoodLogRepository;

  public FoodLogSyncEntityHandler(
      FoodLogService foodLogService, UserFoodLogRepository userFoodLogRepository) {
    this.foodLogService = foodLogService;
    this.userFoodLogRepository = userFoodLogRepository;
  }

  @Override
  public SyncEntityType type() {
    return SyncEntityType.FOOD_LOG;
  }

  @Override
  public SyncApplyResult apply(UUID userId, SyncOperation operation) {
    if (operation.operation() == SyncOperationType.DELETE) {
      return foodLogService.softDeleteFromSync(userId, operation.clientUuid(), operation.updatedAt());
    }
    return foodLogService.upsertFromSync(
        userId, operation.clientUuid(), operation.updatedAt(), operation.data());
  }

  @Override
  public List<SyncChange> changesSince(
      UUID userId, long sinceSeq, Instant settledBefore, int limitPlusOne) {
    return userFoodLogRepository
        .findSyncChanges(userId, sinceSeq, settledBefore, PageRequest.of(0, limitPlusOne))
        .stream()
        .map(this::toSyncChange)
        .toList();
  }

  @Override
  public int cleanupTombstonesBefore(Instant cutoff) {
    return userFoodLogRepository.deleteTombstonesBefore(cutoff);
  }

  private SyncChange toSyncChange(UserFoodLog entry) {
    boolean deleted = entry.getDeletedAt() != null;
    SyncOperationType op = deleted ? SyncOperationType.DELETE : SyncOperationType.UPSERT;
    Map<String, Object> data = deleted ? Map.of() : toSyncData(entry);
    return new SyncChange(
        SyncEntityType.FOOD_LOG,
        entry.getClientUuid(),
        op,
        data,
        entry.getUpdatedAt(),
        entry.getSyncSeq());
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
    if (entry.getAiPredictedClassId() != null) {
      map.put("aiPredictedClassId", entry.getAiPredictedClassId());
    }
    return map;
  }
}
