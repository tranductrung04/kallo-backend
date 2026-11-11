package com.healthcare.kallo.service.act;

import com.healthcare.kallo.dto.request.act.CreateWeightLogRequest;
import com.healthcare.kallo.dto.request.act.UpdateWeightLogRequest;
import com.healthcare.kallo.dto.response.act.WeightLogResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface WeightLogService {
  CreateResult create(UUID userId, CreateWeightLogRequest request);

  Optional<WeightLogResponse> getById(UUID userId, UUID id);

  List<WeightLogResponse> list(UUID userId, LocalDate from, LocalDate to);

  WeightLogResponse update(UUID userId, UUID id, UpdateWeightLogRequest request);

  void delete(UUID userId, UUID id);

  List<WeightLogResponse> changedSince(UUID userId, Instant since);

  WeightLogResponse upsertFromClient(
      UUID userId, CreateWeightLogRequest request, Instant clientUpdatedAt, Instant clientDeletedAt);

  record CreateResult(WeightLogResponse response, boolean created) {}
}
