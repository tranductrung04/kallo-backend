package com.healthcare.kallo.service.act;

import com.healthcare.kallo.common.PageResponse;
import com.healthcare.kallo.dto.request.act.*;
import com.healthcare.kallo.dto.response.act.ActivityResponse;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.List;

public interface ActivityLogService {
  CreateResult create(UUID userId, CreateActivityRequest request);
  Optional<ActivityResponse> getById(UUID userId, UUID id);
  PageResponse<ActivityResponse> list(UUID userId, LocalDate from, LocalDate to, int page, int size,
      boolean includeSuperseded);
  ActivityResponse update(UUID userId, UUID id, UpdateActivityRequest request);
  void delete(UUID userId, UUID id);
  void deleteByClientUuid(UUID userId, UUID clientUuid);

  List<ActivityResponse> changedSince(UUID userId, Instant since);

  ActivityResponse upsertFromClient(
      UUID userId, CreateActivityRequest request, Instant clientUpdatedAt, Instant clientDeletedAt);
  record CreateResult(ActivityResponse response, boolean created) {}
}
