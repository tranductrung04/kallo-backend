package com.healthcare.kallo.service.act.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.ActProperties;
import com.healthcare.kallo.dto.request.act.CreateWeightLogRequest;
import com.healthcare.kallo.dto.request.act.UpdateWeightLogRequest;
import com.healthcare.kallo.dto.response.act.WeightLogResponse;
import com.healthcare.kallo.event.act.WeightChangedEvent;
import com.healthcare.kallo.mapper.WeightLogMapper;
import com.healthcare.kallo.model.act.WeightLog;
import com.healthcare.kallo.repository.act.WeightLogRepository;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.service.act.UserTimeResolver;
import com.healthcare.kallo.service.act.WeightLogService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeightLogServiceImpl implements WeightLogService {
  private final WeightLogRepository weightLogs;
  private final UserRepository users;
  private final UserTimeResolver userTimeResolver;
  private final WeightLogMapper mapper;
  private final ApplicationEventPublisher events;
  private final ActProperties properties;
  private final Clock clock;

  public WeightLogServiceImpl(
      WeightLogRepository weightLogs,
      UserRepository users,
      UserTimeResolver userTimeResolver,
      WeightLogMapper mapper,
      ApplicationEventPublisher events,
      ActProperties properties,
      Clock clock) {
    this.weightLogs = weightLogs;
    this.users = users;
    this.userTimeResolver = userTimeResolver;
    this.mapper = mapper;
    this.events = events;
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  @Transactional
  public WeightLogService.CreateResult create(UUID userId, CreateWeightLogRequest request) {
    validateUserExists(userId);
    validateWeight(request.weightKg());
    Instant loggedAt = request.loggedAtOrNow();
    validateLoggedAt(loggedAt);

    UUID clientUuid = request.clientUuid();
    int inserted =
        weightLogs.insertIfAbsent(userId, clientUuid, request.weightKg(), loggedAt, request.note());
    if (inserted == 0) {
      WeightLog weightLog =
          weightLogs.findByUserIdAndClientUuid(userId, clientUuid)
              .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
      if (weightLog.getDeletedAt() != null) {
        throw new BusinessException(ErrorCode.RECORD_DELETED);
      }
      return new WeightLogService.CreateResult(mapper.toResponse(weightLog), false);
    }

    WeightLog saved =
        weightLogs.findByUserIdAndClientUuid(userId, clientUuid)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    publishChangedEvent(userId, saved.getLoggedAt());
    return new WeightLogService.CreateResult(mapper.toResponse(saved), true);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<WeightLogResponse> getById(UUID userId, UUID id) {
    return weightLogs.findByIdAndUserIdAndDeletedAtIsNull(id, userId).map(mapper::toResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public List<WeightLogResponse> list(UUID userId, LocalDate from, LocalDate to) {
    if (from == null && to == null) {
      ZoneId zone = userTimeResolver.zoneOf(userId);
      LocalDate today = LocalDate.now(clock.withZone(zone));
      from = today.minusDays(90);
      to = today;
    }
    if (from == null || to == null) {
      throw new BusinessException(ErrorCode.INVALID_TIME_RANGE);
    }
    if (from.isAfter(to)) {
      throw new BusinessException(ErrorCode.INVALID_TIME_RANGE);
    }
    if (from.plusDays(365).isBefore(to)) {
      throw new BusinessException(ErrorCode.INVALID_TIME_RANGE);
    }

    ZoneId zone = userTimeResolver.zoneOf(userId);
    Instant fromInstant = userTimeResolver.dayRange(from, zone).start();
    Instant toInstant = userTimeResolver.dayRange(to, zone).end();
    return weightLogs.findByUserIdAndLoggedAtInRange(userId, fromInstant, toInstant)
        .stream()
        .map(mapper::toResponse)
        .toList();
  }

  @Override
  @Transactional
  public WeightLogResponse update(UUID userId, UUID id, UpdateWeightLogRequest request) {
    WeightLog existing =
        weightLogs
            .findByIdAndUserId(id, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    if (existing.getDeletedAt() != null) {
      throw new BusinessException(ErrorCode.RECORD_DELETED);
    }

    Instant clientUpdatedAt = request.clientUpdatedAt();
    if (clientUpdatedAt != null && clientUpdatedAt.isBefore(existing.getUpdatedAt())) {
      return mapper.toResponse(existing);
    }

    validateWeight(request.weightKg());
    validateLoggedAt(request.loggedAt());
    LocalDate oldDate = localDate(userId, existing.getLoggedAt());
    existing.setWeightKg(request.weightKg());
    existing.setLoggedAt(request.loggedAt());
    existing.setNote(request.note());
    WeightLog updated = weightLogs.saveAndFlush(existing);
    publishChangedEvent(userId, updated.getLoggedAt());
    LocalDate newDate = localDate(userId, updated.getLoggedAt());
    if (!oldDate.equals(newDate)) {
      events.publishEvent(new WeightChangedEvent(userId, oldDate));
    }
    return mapper.toResponse(updated);
  }

  @Override
  @Transactional
  public void delete(UUID userId, UUID id) {
    WeightLog existing =
        weightLogs
            .findByIdAndUserId(id, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    if (existing.getDeletedAt() == null) {
      int updated = weightLogs.softDeleteByIdAndUserId(id, userId, clock.instant());
      if (updated > 0) {
        publishChangedEvent(userId, existing.getLoggedAt());
      }
    }
  }

  private void validateUserExists(UUID userId) {
    if (!users.existsById(userId)) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
  }

  private void validateWeight(BigDecimal weightKg) {
    if (weightKg == null) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
    if (weightKg.compareTo(properties.weightMinKg()) < 0
        || weightKg.compareTo(properties.weightMaxKg()) > 0) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
  }

  private void validateLoggedAt(Instant loggedAt) {
    Instant now = clock.instant();
    if (loggedAt.isAfter(now.plus(properties.futureTolerance()))
        || loggedAt.isBefore(
            now.atZone(java.time.ZoneOffset.UTC).minusYears(10).toInstant())) {
      throw new BusinessException(ErrorCode.TIMESTAMP_OUT_OF_RANGE);
    }
  }

  private LocalDate localDate(UUID userId, Instant instant) {
    return instant.atZone(userTimeResolver.zoneOf(userId)).toLocalDate();
  }

  private void publishChangedEvent(UUID userId, Instant instant) {
    ZoneId zone = userTimeResolver.zoneOf(userId);
    LocalDate localDate = instant.atZone(zone).toLocalDate();
    events.publishEvent(new WeightChangedEvent(userId, localDate));
  }
}
