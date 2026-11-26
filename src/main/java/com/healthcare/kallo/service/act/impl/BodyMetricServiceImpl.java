package com.healthcare.kallo.service.act.impl;

import com.healthcare.kallo.common.*;
import com.healthcare.kallo.config.ActProperties;
import com.healthcare.kallo.dto.request.act.*;
import com.healthcare.kallo.dto.response.act.BodyMetricResponse;
import com.healthcare.kallo.mapper.BodyMetricMapper;
import com.healthcare.kallo.model.act.BodyMetric;
import com.healthcare.kallo.repository.act.BodyMetricRepository;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.service.act.BodyMetricService;
import com.healthcare.kallo.service.act.ProfileBodyMetrics;
import com.healthcare.kallo.service.act.UserTimeResolver;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BodyMetricServiceImpl implements BodyMetricService {
  private final BodyMetricRepository metrics;
  private final UserRepository users;
  private final UserTimeResolver time;
  private final BodyMetricMapper mapper;
  private final ActProperties properties;
  private final ObjectProvider<ProfileBodyMetrics> profileBodyMetrics;
  private final Clock clock;

  public BodyMetricServiceImpl(BodyMetricRepository metrics, UserRepository users, UserTimeResolver time,
      BodyMetricMapper mapper, ActProperties properties,
      ObjectProvider<ProfileBodyMetrics> profileBodyMetrics, Clock clock) {
    this.metrics = metrics; this.users = users; this.time = time; this.mapper = mapper;
    this.properties = properties; this.profileBodyMetrics = profileBodyMetrics; this.clock = clock;
  }

  @Override @Transactional
  public CreateResult create(UUID userId, CreateBodyMetricRequest request) {
    if (!users.existsById(userId)) throw new BusinessException(ErrorCode.NOT_FOUND);
    validate(request.waistCm(), request.hipCm());
    Instant loggedAt = request.loggedAtOrNow(); validateTime(loggedAt);
    int inserted = metrics.insertIfAbsent(userId, request.clientUuid(), request.waistCm(), request.hipCm(), loggedAt, request.note());
    BodyMetric metric = metrics.findByUserIdAndClientUuid(userId, request.clientUuid()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    if (inserted == 0 && metric.getDeletedAt() != null) throw new BusinessException(ErrorCode.RECORD_DELETED);
    return new CreateResult(response(userId, metric), inserted > 0);
  }

  @Override @Transactional(readOnly = true)
  public Optional<BodyMetricResponse> getById(UUID userId, UUID id) {
    return metrics.findByIdAndUserIdAndDeletedAtIsNull(id, userId).map(metric -> response(userId, metric));
  }

  @Override @Transactional(readOnly = true)
  public List<BodyMetricResponse> list(UUID userId, LocalDate from, LocalDate to) {
    ZoneId zone = time.zoneOf(userId);
    LocalDate today = LocalDate.now(clock.withZone(zone));
    if (from == null && to == null) { from = today.minusDays(90); to = today; }
    if (from == null || to == null || from.isAfter(to) || from.plusDays(365).isBefore(to)) throw new BusinessException(ErrorCode.INVALID_TIME_RANGE);
    Instant start = time.dayRange(from, zone).start(), end = time.dayRange(to, zone).end();
    return metrics.findByUserIdAndLoggedAtBetweenAndDeletedAtIsNullOrderByLoggedAtAsc(userId, start, end).stream().map(metric -> response(userId, metric)).toList();
  }

  @Override @Transactional
  public BodyMetricResponse update(UUID userId, UUID id, UpdateBodyMetricRequest request) {
    BodyMetric metric = metrics.findByIdAndUserId(id, userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    if (metric.getDeletedAt() != null) throw new BusinessException(ErrorCode.RECORD_DELETED);
    if (request.clientUpdatedAt() != null && request.clientUpdatedAt().isBefore(metric.getUpdatedAt())) return response(userId, metric);
    validate(request.waistCm(), request.hipCm()); validateTime(request.loggedAt());
    metric.setWaistCm(request.waistCm()); metric.setHipCm(request.hipCm()); metric.setLoggedAt(request.loggedAt()); metric.setNote(request.note());
    return response(userId, metrics.saveAndFlush(metric));
  }

  @Override @Transactional
  public void delete(UUID userId, UUID id) {
    if (metrics.findByIdAndUserId(id, userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)).getDeletedAt() == null)
      metrics.softDeleteByIdAndUserId(id, userId, clock.instant());
  }

  @Override @Transactional(readOnly = true)
  public List<BodyMetricResponse> changedSince(UUID userId, Instant since, int limit) {
    if (since == null || limit < 1 || limit > 500) throw new BusinessException(ErrorCode.INVALID_PARAMETER);
    return metrics.findChangedSince(userId, since.minusSeconds(1), PageRequest.of(0, limit)).stream().map(metric -> response(userId, metric)).toList();
  }

  private BodyMetricResponse response(UUID userId, BodyMetric metric) {
    BodyMetricResponse mapped = mapper.toResponse(metric, time.zoneOf(userId));
    ProfileBodyMetrics profileMetrics = profileBodyMetrics.getIfAvailable();
    String gender = profileMetrics == null ? null : profileMetrics.gender(userId).orElse(null);
    var risk = com.healthcare.kallo.service.act.WhrRiskCalculator.calculate(
        mapped.waistCm(), mapped.hipCm(), gender);
    return new BodyMetricResponse(mapped.id(), mapped.userId(), mapped.clientUuid(), mapped.waistCm(), mapped.hipCm(),
        mapped.loggedAt(), mapped.localDate(), risk.whr(), risk.riskLevel(), gender, mapped.note(),
        mapped.createdAt(), mapped.updatedAt(), mapped.deletedAt());
  }
  private void validate(BigDecimal waist, BigDecimal hip) { if (waist == null || hip == null || waist.signum() <= 0 || hip.signum() <= 0) throw new BusinessException(ErrorCode.VALIDATION_ERROR); }
  private void validateTime(Instant value) { if (value.isAfter(clock.instant().plus(properties.futureTolerance())) || value.isBefore(clock.instant().minus(Duration.ofDays(properties.manualBackfillDays())))) throw new BusinessException(ErrorCode.TIMESTAMP_OUT_OF_RANGE); }
}
