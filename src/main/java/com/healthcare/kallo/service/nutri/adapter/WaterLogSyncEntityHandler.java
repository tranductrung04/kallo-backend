package com.healthcare.kallo.service.nutri.adapter;

import com.healthcare.kallo.model.nutri.WaterLog;
import com.healthcare.kallo.repository.nutri.WaterLogRepository;
import com.healthcare.kallo.service.nutri.WaterLogService;
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

/** Offline sync handler delegating WATER_LOG mutations through WaterLogService. */
@Component
public class WaterLogSyncEntityHandler implements SyncEntityHandler {

  private final WaterLogService waterLogService;
  private final WaterLogRepository waterLogRepository;

  public WaterLogSyncEntityHandler(
      WaterLogService waterLogService, WaterLogRepository waterLogRepository) {
    this.waterLogService = waterLogService;
    this.waterLogRepository = waterLogRepository;
  }

  @Override
  public SyncEntityType type() {
    return SyncEntityType.WATER_LOG;
  }

  @Override
  public SyncApplyResult apply(UUID userId, SyncOperation operation) {
    if (operation.operation() == SyncOperationType.DELETE) {
      return waterLogService.softDeleteFromSync(userId, operation.clientUuid(), operation.updatedAt());
    }
    return waterLogService.upsertFromSync(
        userId, operation.clientUuid(), operation.updatedAt(), operation.data());
  }

  @Override
  public List<SyncChange> changesSince(
      UUID userId, long sinceSeq, Instant settledBefore, int limitPlusOne) {
    return waterLogRepository
        .findSyncChanges(userId, sinceSeq, settledBefore, PageRequest.of(0, limitPlusOne))
        .stream()
        .map(this::toSyncChange)
        .toList();
  }

  @Override
  public int cleanupTombstonesBefore(Instant cutoff) {
    return waterLogRepository.deleteTombstonesBefore(cutoff);
  }

  private SyncChange toSyncChange(WaterLog entry) {
    boolean deleted = entry.getDeletedAt() != null;
    SyncOperationType op = deleted ? SyncOperationType.DELETE : SyncOperationType.UPSERT;
    Map<String, Object> data = deleted ? Map.of() : toSyncData(entry);
    return new SyncChange(
        SyncEntityType.WATER_LOG,
        entry.getClientUuid(),
        op,
        data,
        entry.getUpdatedAt(),
        entry.getSyncSeq());
  }

  private Map<String, Object> toSyncData(WaterLog entry) {
    Map<String, Object> map = new LinkedHashMap<>();
    if (entry.getAmountMl() != null) map.put("amountMl", entry.getAmountMl());
    if (entry.getLoggedDate() != null) map.put("loggedDate", entry.getLoggedDate().toString());
    if (entry.getLoggedAt() != null) map.put("loggedAt", entry.getLoggedAt().toString());
    if (entry.getNote() != null) map.put("note", entry.getNote());
    return map;
  }
}
