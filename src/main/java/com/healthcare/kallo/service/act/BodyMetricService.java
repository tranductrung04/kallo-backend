package com.healthcare.kallo.service.act;

import com.healthcare.kallo.dto.request.act.*;
import com.healthcare.kallo.dto.response.act.BodyMetricResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BodyMetricService {
  record CreateResult(BodyMetricResponse response, boolean created) {}
  CreateResult create(UUID userId, CreateBodyMetricRequest request);
  Optional<BodyMetricResponse> getById(UUID userId, UUID id);
  List<BodyMetricResponse> list(UUID userId, LocalDate from, LocalDate to);
  BodyMetricResponse update(UUID userId, UUID id, UpdateBodyMetricRequest request);
  void delete(UUID userId, UUID id);
  List<BodyMetricResponse> changedSince(UUID userId, Instant since, int limit);
}
