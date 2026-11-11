package com.healthcare.kallo.service.act;

import com.healthcare.kallo.common.PageResponse;
import com.healthcare.kallo.dto.request.act.*;
import com.healthcare.kallo.dto.response.act.ActivityResponse;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ActivityLogService {
  CreateResult create(UUID userId, CreateActivityRequest request);
  Optional<ActivityResponse> getById(UUID userId, UUID id);
  PageResponse<ActivityResponse> list(UUID userId, LocalDate from, LocalDate to, int page, int size,
      boolean includeSuperseded);
  ActivityResponse update(UUID userId, UUID id, UpdateActivityRequest request);
  void delete(UUID userId, UUID id);
  void deleteByClientUuid(UUID userId, UUID clientUuid);
  record CreateResult(ActivityResponse response, boolean created) {}
}
