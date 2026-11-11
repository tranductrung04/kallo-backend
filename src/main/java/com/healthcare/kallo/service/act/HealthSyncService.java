package com.healthcare.kallo.service.act;

import com.healthcare.kallo.dto.request.act.HealthSyncRequest;
import com.healthcare.kallo.dto.response.act.HealthSyncResponse;
import com.healthcare.kallo.dto.response.act.HealthSyncStatusResponse;
import java.util.List;
import java.util.UUID;

public interface HealthSyncService {
  HealthSyncResponse sync(UUID userId, HealthSyncRequest request);
  List<HealthSyncStatusResponse> status(UUID userId);
}
