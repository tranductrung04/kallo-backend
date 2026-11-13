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
  private final ActivityDeduplicatorService deduplicator;

  public ActivityLogServiceImpl(ActivityLogRepository activities, ActivityTypeRepository types,
      BodyMetricsService bodyMetrics, UserTimeResolver timeResolver,
      ActivityMapper mapper, ApplicationEventPublisher events, ActProperties properties, Clock clock,
      ActivityDeduplicatorService deduplicator) {
    this.activities = activities; this.types = types; this.bodyMetrics = bodyMetrics;
    this.timeResolver = timeResolver; this.mapper = mapper; this.events = events;
    this.properties = properties; this.clock = clock;
    this.deduplicator = deduplicator;
  }

  @Override
  @Transactional
  public CreateResult create(UUID userId, CreateActivityRequest request) {
    UUID clientUuid = toUuid(request.clientUuid());
    Optional<ActivityLog> existing = activities.findByUserIdAndClientUuid(userId, clientUuid);
    if (existing.isPresent()) {
      if (existing.get().getDeletedAt() != null) throw new BusinessException(ErrorCode.RECORD_DELETED);
      return new CreateResult(mapper.toResponse(existing.get()).withoutRoute(), false);
    }
    validateTime(request.startedAt());
    ActivityType type = activeType(request.activityType());
    validateDistance(type, request.distanceKm());
    String source = source(request.source());
    BigDecimal distance = validateRoute(source, request.routePolyline(), request.distanceKm(), request.durationMin());
    BigDecimal weight = bodyMetrics.weightAt(userId, request.startedAt())
        .orElseThrow(() -> new BusinessException(ErrorCode.WEIGHT_REQUIRED));
    BigDecimal met = met(type, request.intensity());
    Instant ended = request.startedAt().plusSeconds(request.durationMin() * 60L);
    BigDecimal calories = CalorieCalculator.calculate(met, weight, request.durationMin());
    int inserted = activities.insertIfAbsent(userId, clientUuid, type.getCode(),
        request.intensity().name(), met, weight, request.durationMin(), distance,
        calories, source, request.externalId(), request.startedAt(), ended, request.note(), request.routePolyline());
    ActivityLog result = activities.findByUserIdAndClientUuid(userId, clientUuid)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    if (inserted == 0) {
      if (result.getDeletedAt() != null) throw new BusinessException(ErrorCode.RECORD_DELETED);
      return new CreateResult(mapper.toResponse(result).withoutRoute(), false);
    }
    deduplicator.deduplicate(userId, result.getStartedAt(), result.getEndedAt());
    publish(userId, result.getStartedAt());
    return new CreateResult(mapper.toResponse(result).withoutRoute(), true);
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
    return new PageResponse<>(result.getContent().stream().map(mapper::toResponse)
        .map(ActivityResponse::withoutRoute).toList(),
        page, size, result.getTotalElements(), result.getTotalPages());
  }

  @Override
  @Transactional
  public ActivityResponse update(UUID userId, UUID id, UpdateActivityRequest request) {
    ActivityLog activity = activities.findByIdAndUserId(id, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    if (activity.getDeletedAt() != null) throw new BusinessException(ErrorCode.RECORD_DELETED);
    if (request.clientUpdatedAt() != null && request.clientUpdatedAt().isBefore(activity.getUpdatedAt()))
      return mapper.toResponse(activity).withoutRoute();
    validateTime(request.startedAt());
    ActivityType type = activeType(request.activityType());
    validateDistance(type, request.distanceKm());
    String source = activity.getSource().name();
    String routePolyline = request.routePolyline() == null
        ? activity.getRoutePolyline() : request.routePolyline();
    BigDecimal distance = validateRoute(source, routePolyline, request.distanceKm(), request.durationMin());
    BigDecimal weight = bodyMetrics.weightAt(userId, request.startedAt())
        .orElseThrow(() -> new BusinessException(ErrorCode.WEIGHT_REQUIRED));
    BigDecimal met = met(type, request.intensity());
    LocalDate oldDate = localDate(userId, activity.getStartedAt());
    Instant oldStartedAt = activity.getStartedAt();
    Instant oldEndedAt = activity.getEndedAt();
    activity.setActivityType(type); activity.setIntensity(request.intensity()); activity.setStartedAt(request.startedAt());
    activity.setDurationMin(request.durationMin()); activity.setEndedAt(request.startedAt().plusSeconds(request.durationMin() * 60L));
    activity.setDistanceKm(distance); activity.setMet(met); activity.setWeightKgUsed(weight);
    activity.setCaloriesBurned(CalorieCalculator.calculate(met, weight, request.durationMin()));
    activity.setNote(request.note());
    activity.setRoutePolyline(routePolyline);
    ActivityLog saved = activities.saveAndFlush(activity);
    deduplicator.deduplicate(userId,
        min(oldStartedAt, saved.getStartedAt()), max(oldEndedAt, saved.getEndedAt()));
    publish(userId, saved.getStartedAt());
    LocalDate newDate = localDate(userId, saved.getStartedAt());
    if (!oldDate.equals(newDate)) events.publishEvent(new ActivityDataChangedEvent(userId, oldDate));
    return mapper.toResponse(saved).withoutRoute();
  }

  @Override
  @Transactional
  public void delete(UUID userId, UUID id) {
    ActivityLog activity = activities.findByIdAndUserId(id, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    if (activity.getDeletedAt() == null) {
      if (activities.softDeleteByIdAndUserId(id, userId, clock.instant()) > 0) {
        publish(userId, activity.getStartedAt());
        deduplicator.deduplicate(userId, activity.getStartedAt(), activity.getEndedAt());
      }
    }
  }

  @Override
  @Transactional
  public void deleteByClientUuid(UUID userId, UUID clientUuid) {
    ActivityLog activity = activities.findByUserIdAndClientUuid(userId, clientUuid)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    delete(userId, activity.getId());
  }

  @Override
  @Transactional(readOnly = true)
  public List<ActivityResponse> changedSince(UUID userId, Instant since) {
    return changedSince(userId, since, 500);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ActivityResponse> changedSince(UUID userId, Instant since, int limit) {
    if (since == null) throw new BusinessException(ErrorCode.INVALID_PARAMETER);
    if (limit < 1 || limit > 500) throw new BusinessException(ErrorCode.INVALID_PARAMETER);
    return activities.findChangedSince(userId, since.minusSeconds(1), PageRequest.of(0, limit)).stream()
        .map(mapper::toResponse)
        .map(ActivityResponse::withoutRoute)
        .toList();
  }

  @Override
  @Transactional
  public ActivityResponse upsertFromClient(
      UUID userId, CreateActivityRequest request, Instant clientUpdatedAt, Instant clientDeletedAt) {
    UUID clientUuid = toUuid(request.clientUuid());
    Optional<ActivityLog> existing = activities.findByUserIdAndClientUuid(userId, clientUuid);
    if (clientDeletedAt != null) {
      if (existing.isEmpty()) return null;
      if (clientUpdatedAt == null || existing.get().getUpdatedAt() == null
          || !clientUpdatedAt.isBefore(existing.get().getUpdatedAt())) {
        delete(userId, existing.get().getId());
      }
      return activities.findByIdAndUserId(existing.get().getId(), userId)
          .map(mapper::toResponse).map(ActivityResponse::withoutRoute).orElse(null);
    }
    if (existing.isPresent() && clientUpdatedAt != null && existing.get().getUpdatedAt() != null
        && clientUpdatedAt.isBefore(existing.get().getUpdatedAt())) {
      return mapper.toResponse(existing.get()).withoutRoute();
    }
    if (existing.isPresent()) {
      return update(userId, existing.get().getId(), new UpdateActivityRequest(
          request.activityType(), request.intensity(), request.startedAt(), request.durationMin(),
          request.distanceKm(), request.note(), clientUpdatedAt, request.routePolyline()));
    }
    return create(userId, request).response();
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
    if (source == null || source.isBlank()) return "MANUAL";
    String normalized = source.trim().toUpperCase(Locale.ROOT);
    if (normalized.equals("MANUAL") || normalized.equals("GPS") || normalized.equals("SENSOR")) {
      return normalized;
    }
    if (normalized.equals("APPLE_HEALTH") || normalized.equals("HEALTH_CONNECT")
        || normalized.equals("HEALTHKIT")) return normalized.equals("APPLE_HEALTH") || normalized.equals("HEALTHKIT")
            ? "HEALTHKIT" : "HEALTH_CONNECT";
    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
        Map.of("field", "source", "message", "Source must be MANUAL, GPS, or SENSOR"));
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
  private BigDecimal validateRoute(String source, String routePolyline, BigDecimal distance, int durationMin) {
    if (source.equals("GPS") && (routePolyline == null || routePolyline.isBlank())) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR,
          Map.of("field", "routePolyline", "message", "GPS activities require a route"));
    }
    if (routePolyline == null) return distance;
    if (!source.equals("GPS")) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR,
          Map.of("field", "routePolyline", "message", "Route requires GPS source"));
    }
    if (routePolyline.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > properties.routeMaxBytes()) {
      throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE,
          Map.of("field", "routePolyline", "message", "Route polyline is too large"));
    }
    List<Point> points = decodePoints(routePolyline);
    double routeDistanceKm = routeDistanceKm(points);
    double maxDistanceKm = properties.gpsMaxSpeedKmh().doubleValue() * durationMin / 60.0;
    if (routeDistanceKm > maxDistanceKm) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR,
          Map.of("field", "routePolyline", "message", "GPS route speed is not plausible"));
    }
    if (distance != null && Math.abs(distance.doubleValue() - routeDistanceKm)
        > Math.max(0.1, routeDistanceKm * 0.2)) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR,
          Map.of("field", "distanceKm", "message", "Distance does not match route"));
    }
    return distance == null ? BigDecimal.valueOf(routeDistanceKm).setScale(2, java.math.RoundingMode.HALF_UP) : distance;
  }
  private List<Point> decodePoints(String polyline) {
    if (polyline.isBlank()) throw invalidPolyline();
    int index = 0;
    long latitude = 0, longitude = 0;
    List<Point> points = new ArrayList<>();
    try {
      while (index < polyline.length()) {
        long[] lat = decodePolylineValue(polyline, index);
        index = (int) lat[1];
        long[] lon = decodePolylineValue(polyline, index);
        index = (int) lon[1];
        latitude += lat[0];
        longitude += lon[0];
        if (latitude < -9000000 || latitude > 9000000
            || longitude < -18000000 || longitude > 18000000) throw invalidPolyline();
        points.add(new Point(latitude / 100000.0, longitude / 100000.0));
      }
    } catch (IllegalArgumentException exception) {
      throw invalidPolyline();
    }
    if (points.size() < 2) throw invalidPolyline();
    return points;
  }
  private BusinessException invalidPolyline() {
    return new BusinessException(ErrorCode.VALIDATION_ERROR,
        Map.of("field", "routePolyline", "message", "Route polyline is invalid"));
  }
    private long[] decodePolylineValue(String value, int start) {
      long result = 0; int shift = 0; int index = start;
      while (index < value.length()) {
        int chunk = value.charAt(index++) - 63;
        if (chunk < 0 || chunk > 63) throw new IllegalArgumentException();
        result |= (long) (chunk & 0x1f) << shift;
        if ((chunk & 0x20) == 0) {
          long decoded = (result & 1) != 0 ? ~(result >> 1) : result >> 1;
          return new long[] {decoded, index};
        }
        shift += 5;
        if (shift > 60) throw new IllegalArgumentException();
      }
      throw new IllegalArgumentException();
    }
  private double routeDistanceKm(List<Point> points) {
    double distance = 0;
    for (int i = 1; i < points.size(); i++) distance += haversineKm(points.get(i - 1), points.get(i));
    return distance;
  }
  private double haversineKm(Point left, Point right) {
    double lat1 = Math.toRadians(left.latitude());
    double lat2 = Math.toRadians(right.latitude());
    double dLat = lat2 - lat1;
    double dLon = Math.toRadians(right.longitude() - left.longitude());
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
        + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    return 6371.0088 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }
  private record Point(double latitude, double longitude) {}
  private void validateTime(Instant startedAt) {
    Instant now = clock.instant();
    if (startedAt.isAfter(now.plus(properties.futureTolerance()))
        || startedAt.isBefore(now.minus(Duration.ofDays(properties.manualBackfillDays()))))
      throw new BusinessException(ErrorCode.TIMESTAMP_OUT_OF_RANGE);
  }
  private LocalDate localDate(UUID userId, Instant instant) { return instant.atZone(timeResolver.zoneOf(userId)).toLocalDate(); }
  private Instant min(Instant left, Instant right) { return left.isBefore(right) ? left : right; }
  private Instant max(Instant left, Instant right) { return left.isAfter(right) ? left : right; }
  private void publish(UUID userId, Instant instant) { events.publishEvent(new ActivityDataChangedEvent(userId, localDate(userId, instant))); }
}
