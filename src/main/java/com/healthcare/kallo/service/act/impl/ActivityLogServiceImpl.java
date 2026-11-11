package com.healthcare.kallo.service.act.impl;

import com.healthcare.kallo.common.*;
import com.healthcare.kallo.config.ActProperties;
import com.healthcare.kallo.dto.request.act.*;
import com.healthcare.kallo.dto.response.act.ActivityResponse;
import com.healthcare.kallo.event.act.ActivityDataChangedEvent;
import com.healthcare.kallo.mapper.ActivityMapper;
import com.healthcare.kallo.model.act.*;
import com.healthcare.kallo.model.act.enums.Intensity;
import com.healthcare.kallo.repository.act.*;
import com.healthcare.kallo.service.act.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityLogServiceImpl implements ActivityLogService {
  private final ActivityLogRepository activities;
  private final ActivityTypeRepository types;
  private final BodyMetricsService bodyMetrics;
  private final UserTimeResolver timeResolver;
  private final ActivityMapper mapper;
  private final ApplicationEventPublisher events;
  private final ActProperties properties;
  private final Clock clock;

  public ActivityLogServiceImpl(ActivityLogRepository activities, ActivityTypeRepository types,
      BodyMetricsService bodyMetrics, UserTimeResolver timeResolver,
      ActivityMapper mapper, ApplicationEventPublisher events, ActProperties properties, Clock clock) {
    this.activities = activities; this.types = types; this.bodyMetrics = bodyMetrics;
    this.timeResolver = timeResolver; this.mapper = mapper; this.events = events;
    this.properties = properties; this.clock = clock;
  }

  @Override
  @Transactional
  public CreateResult create(UUID userId, CreateActivityRequest request) {
    UUID clientUuid = toUuid(request.clientUuid());
    Optional<ActivityLog> existing = activities.findByUserIdAndClientUuid(userId, clientUuid);
    if (existing.isPresent()) {
      if (existing.get().getDeletedAt() != null) throw new BusinessException(ErrorCode.RECORD_DELETED);
      return new CreateResult(mapper.toResponse(existing.get()), false);
    }
    validateTime(request.startedAt());
    ActivityType type = activeType(request.activityType());
    validateDistance(type, request.distanceKm());
    BigDecimal weight = bodyMetrics.weightAt(userId, request.startedAt())
        .orElseThrow(() -> new BusinessException(ErrorCode.WEIGHT_REQUIRED));
    BigDecimal met = met(type, request.intensity());
    Instant ended = request.startedAt().plusSeconds(request.durationMin() * 60L);
    BigDecimal calories = CalorieCalculator.calculate(met, weight, request.durationMin());
    int inserted = activities.insertIfAbsent(userId, clientUuid, type.getCode(),
        request.intensity().name(), met, weight, request.durationMin(), request.distanceKm(),
        calories, source(request.source()), request.externalId(), request.startedAt(), ended, request.note());
    ActivityLog result = activities.findByUserIdAndClientUuid(userId, clientUuid)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    if (inserted == 0) {
      if (result.getDeletedAt() != null) throw new BusinessException(ErrorCode.RECORD_DELETED);
      return new CreateResult(mapper.toResponse(result), false);
    }
    publish(userId, result.getStartedAt());
    return new CreateResult(mapper.toResponse(result), true);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ActivityResponse> getById(UUID userId, UUID id) {
    return activities.findByIdAndUserIdAndDeletedAtIsNull(id, userId).map(mapper::toResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<ActivityResponse> list(UUID userId, LocalDate from, LocalDate to, int page,
      int size, boolean includeSuperseded) {
    if (size < 1 || size > 200 || page < 0) throw new BusinessException(ErrorCode.INVALID_PARAMETER);
    ZoneId zone = timeResolver.zoneOf(userId);
    LocalDate today = LocalDate.now(clock.withZone(zone));
    if (from == null && to == null) { from = today.minusDays(90); to = today; }
    if (from == null || to == null || from.isAfter(to) || from.plusDays(365).isBefore(to))
      throw new BusinessException(ErrorCode.INVALID_TIME_RANGE);
    var rangeFrom = timeResolver.dayRange(from, zone).start();
    var rangeTo = timeResolver.dayRange(to, zone).end();
    Page<ActivityLog> result = activities.findInRange(userId, rangeFrom, rangeTo, includeSuperseded,
        PageRequest.of(page, size));
    return new PageResponse<>(result.getContent().stream().map(mapper::toResponse).toList(),
        page, size, result.getTotalElements(), result.getTotalPages());
  }

  @Override
  @Transactional
  public ActivityResponse update(UUID userId, UUID id, UpdateActivityRequest request) {
    ActivityLog activity = activities.findByIdAndUserId(id, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    if (activity.getDeletedAt() != null) throw new BusinessException(ErrorCode.RECORD_DELETED);
    if (request.clientUpdatedAt() != null && request.clientUpdatedAt().isBefore(activity.getUpdatedAt()))
      return mapper.toResponse(activity);
    validateTime(request.startedAt());
    ActivityType type = activeType(request.activityType());
    validateDistance(type, request.distanceKm());
    BigDecimal weight = bodyMetrics.weightAt(userId, request.startedAt())
        .orElseThrow(() -> new BusinessException(ErrorCode.WEIGHT_REQUIRED));
    BigDecimal met = met(type, request.intensity());
    LocalDate oldDate = localDate(userId, activity.getStartedAt());
    activity.setActivityType(type); activity.setIntensity(request.intensity()); activity.setStartedAt(request.startedAt());
    activity.setDurationMin(request.durationMin()); activity.setEndedAt(request.startedAt().plusSeconds(request.durationMin() * 60L));
    activity.setDistanceKm(request.distanceKm()); activity.setMet(met); activity.setWeightKgUsed(weight);
    activity.setCaloriesBurned(CalorieCalculator.calculate(met, weight, request.durationMin()));
    activity.setNote(request.note());
    ActivityLog saved = activities.saveAndFlush(activity);
    publish(userId, saved.getStartedAt());
    LocalDate newDate = localDate(userId, saved.getStartedAt());
    if (!oldDate.equals(newDate)) events.publishEvent(new ActivityDataChangedEvent(userId, oldDate));
    return mapper.toResponse(saved);
  }

  @Override
  @Transactional
  public void delete(UUID userId, UUID id) {
    ActivityLog activity = activities.findByIdAndUserId(id, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    if (activity.getDeletedAt() == null) {
      if (activities.softDeleteByIdAndUserId(id, userId, clock.instant()) > 0) publish(userId, activity.getStartedAt());
    }
  }

  @Override
  @Transactional
  public void deleteByClientUuid(UUID userId, UUID clientUuid) {
    ActivityLog activity = activities.findByUserIdAndClientUuid(userId, clientUuid)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    delete(userId, activity.getId());
  }

  private ActivityType activeType(String code) {
    String normalized = normalizeCode(code);
    return types.findByCodeAndActiveTrue(normalized)
        .orElseThrow(() -> new BusinessException(ErrorCode.ACTIVITY_TYPE_UNKNOWN));
  }

  private String normalizeCode(String code) {
    return switch (code.toLowerCase(Locale.ROOT)) {
      case "walking" -> "WALK_BRISK";
      case "running" -> "RUN_8KMH";
      case "cycling" -> "CYCLING_MODERATE";
      case "swimming" -> "SWIM_GENERAL";
      case "gym" -> "WEIGHT_TRAINING";
      case "yoga" -> "YOGA_HATHA";
      case "badminton" -> "BADMINTON";
      case "pickleball" -> "PICKLEBALL";
      case "football" -> "SOCCER";
      case "jump_rope" -> "JUMP_ROPE";
      case "other" -> "GENERAL_WORKOUT";
      case "d_walking" -> "WALK_BRISK";
      case "d_cycling" -> "CYCLING_MODERATE";
      case "d_hiking" -> "HIKING";
      case "b_soccer" -> "SOCCER";
      case "b_basketball" -> "BASKETBALL";
      case "b_table_tennis" -> "TABLE_TENNIS";
      case "b_swimming" -> "SWIM_GENERAL";
      case "c_badminton" -> "BADMINTON";
      case "c_running" -> "RUNNING_FAST";
      case "c_jogging" -> "JOGGING";
      case "n_jump_rope" -> "JUMP_ROPE";
      case "p_pickleball" -> "PICKLEBALL";
      case "t_weight_training" -> "WEIGHT_TRAINING";
      case "y_yoga" -> "YOGA_HATHA";
      default -> code.replaceFirst("^[a-zA-Z]_", "").toUpperCase(Locale.ROOT);
    };
  }

  private String source(String source) {
    if (source == null || source.isBlank() || source.equalsIgnoreCase("MANUAL")) return "MANUAL";
    if (source.equalsIgnoreCase("APPLE_HEALTH")) return "HEALTHKIT";
    if (source.equalsIgnoreCase("HEALTH_CONNECT")) return "HEALTH_CONNECT";
    if (source.equalsIgnoreCase("HEALTHKIT")) return "HEALTHKIT";
    if (source.equalsIgnoreCase("SENSOR")) return "SENSOR";
    if (source.equalsIgnoreCase("GPS")) return "GPS";
    return "MANUAL";
  }

  private UUID toUuid(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      return UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
  }
  private BigDecimal met(ActivityType type, Intensity intensity) {
    BigDecimal value = switch (intensity) {
      case LIGHT -> type.getMetLight(); case MODERATE -> type.getMetModerate(); case VIGOROUS -> type.getMetVigorous();
    };
    if (value == null) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR,
          Map.of("field", "intensity", "message", "Selected intensity is not supported"));
    }
    return value;
  }
  private void validateDistance(ActivityType type, BigDecimal distance) {
    if (distance != null && !type.isHasDistance()) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR,
          Map.of("field", "distanceKm", "message", "Distance is not supported for this activity"));
    }
  }
  private void validateTime(Instant startedAt) {
    Instant now = clock.instant();
    if (startedAt.isAfter(now.plus(properties.futureTolerance()))
        || startedAt.isBefore(now.minus(Duration.ofDays(properties.manualBackfillDays()))))
      throw new BusinessException(ErrorCode.TIMESTAMP_OUT_OF_RANGE);
  }
  private LocalDate localDate(UUID userId, Instant instant) { return instant.atZone(timeResolver.zoneOf(userId)).toLocalDate(); }
  private void publish(UUID userId, Instant instant) { events.publishEvent(new ActivityDataChangedEvent(userId, localDate(userId, instant))); }
}
