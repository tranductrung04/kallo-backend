package com.healthcare.kallo.service.nutri;

import com.healthcare.kallo.dto.request.nutri.CreateWaterLogRequest;
import com.healthcare.kallo.dto.response.nutri.DailyWaterLogsResponse;
import com.healthcare.kallo.dto.response.nutri.WaterLogResponse;
import com.healthcare.kallo.service.sys.sync.SyncApplyResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** Service managing user water intake logs and offline sync mutations. */
public interface WaterLogService {

  record CreateResult(WaterLogResponse response, boolean created) {}

  CreateResult logWater(UUID userId, CreateWaterLogRequest request);

  DailyWaterLogsResponse getByDate(UUID userId, LocalDate date);

  void delete(UUID userId, UUID id);

  SyncApplyResult upsertFromSync(
      UUID userId, UUID clientUuid, Instant opUpdatedAt, Map<String, Object> data);

  SyncApplyResult softDeleteFromSync(UUID userId, UUID clientUuid, Instant opUpdatedAt);
}
