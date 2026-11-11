package com.healthcare.kallo.service.act.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.ActProperties;
import com.healthcare.kallo.dto.request.act.HealthSyncRecordRequest;
import com.healthcare.kallo.dto.request.act.HealthSyncRequest;
import com.healthcare.kallo.dto.response.act.HealthSyncResponse;
import com.healthcare.kallo.dto.response.act.HealthSyncStatusResponse;
import com.healthcare.kallo.event.act.ActivityDataChangedEvent;
import com.healthcare.kallo.model.act.enums.HealthDataType;
import com.healthcare.kallo.model.act.enums.HealthProvider;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.service.act.HealthSyncService;
import com.healthcare.kallo.service.act.UserTimeResolver;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HealthSyncServiceImpl implements HealthSyncService {
  private static final Logger log = LoggerFactory.getLogger(HealthSyncServiceImpl.class);
  private static final BigDecimal MAX_STEPS = new BigDecimal("100000");
  private static final BigDecimal MIN_HEART_RATE = new BigDecimal("20");
  private static final BigDecimal MAX_HEART_RATE = new BigDecimal("260");

  private final NamedParameterJdbcTemplate jdbc;
  private final UserRepository users;
  private final ActProperties properties;
  private final Clock clock;
  private final UserTimeResolver timeResolver;
  private final ApplicationEventPublisher events;

  public HealthSyncServiceImpl(
      NamedParameterJdbcTemplate jdbc, UserRepository users, ActProperties properties, Clock clock,
      UserTimeResolver timeResolver, ApplicationEventPublisher events) {
    this.jdbc = jdbc;
    this.users = users;
    this.properties = properties;
    this.clock = clock;
    this.timeResolver = timeResolver;
    this.events = events;
  }

  @Override
  @Transactional
  public HealthSyncResponse sync(UUID userId, HealthSyncRequest request) {
    if (!users.existsById(userId)) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    if (request.records().size() > properties.maxSyncBatchSize()) {
      throw new BusinessException(ErrorCode.SYNC_BATCH_TOO_LARGE);
    }

    List<HealthSyncResponse.RejectedRecord> rejected = new ArrayList<>();
    List<ValidRecord> valid = new ArrayList<>();
    Set<String> batchKeys = new HashSet<>();
    for (int i = 0; i < request.records().size(); i++) {
      HealthSyncRecordRequest record = request.records().get(i);
      String reason = validate(record);
      if (reason == null && !batchKeys.add(record.dataType().name() + "\u0000" + record.externalId())) {
        reason = "duplicate externalId in request";
      }
      if (reason == null) {
        valid.add(new ValidRecord(i, record));
      } else {
        rejected.add(new HealthSyncResponse.RejectedRecord(i, reason));
      }
    }

    int created = 0;
    int updated = 0;
    int unchanged = 0;
    if (!valid.isEmpty()) {
      List<ExistingRecord> existing = valid.stream()
          .map(item -> findExisting(userId, request.provider(), item.record().dataType(),
              item.record().externalId()))
          .toList();

      MapSqlParameterSource[] parameters = valid.stream()
          .map(item -> parameters(userId, request.provider(), item.record()))
          .toArray(MapSqlParameterSource[]::new);
      jdbc.batchUpdate("""
          INSERT INTO health_sync_logs
            (user_id, provider, data_type, external_id, value, value_min, value_max,
             data_origin, start_time, end_time, synced_at)
          VALUES
            (:userId, :provider, :dataType, :externalId, :value, :valueMin, :valueMax,
             :dataOrigin, :startTime, :endTime, NOW())
          ON CONFLICT (user_id, provider, data_type, external_id) DO UPDATE SET
            value = EXCLUDED.value,
            value_min = EXCLUDED.value_min,
            value_max = EXCLUDED.value_max,
            data_origin = EXCLUDED.data_origin,
            start_time = EXCLUDED.start_time,
            end_time = EXCLUDED.end_time,
            synced_at = NOW()
          WHERE health_sync_logs.value IS DISTINCT FROM EXCLUDED.value
             OR health_sync_logs.value_min IS DISTINCT FROM EXCLUDED.value_min
             OR health_sync_logs.value_max IS DISTINCT FROM EXCLUDED.value_max
             OR health_sync_logs.data_origin IS DISTINCT FROM EXCLUDED.data_origin
             OR health_sync_logs.start_time IS DISTINCT FROM EXCLUDED.start_time
             OR health_sync_logs.end_time IS DISTINCT FROM EXCLUDED.end_time
          """, parameters);

      for (int i = 0; i < valid.size(); i++) {
        ExistingRecord old = existing.get(i);
        if (old == null) {
          created++;
        } else if (same(old, valid.get(i).record())) {
          unchanged++;
        } else {
          updated++;
        }
      }
      publishChangedEvents(userId, valid);
    }

    log.info("Health sync accepted for user {}: received={}, created={}, updated={}, unchanged={}, rejected={}",
        userId, request.records().size(), created, updated, unchanged, rejected.size());
    return new HealthSyncResponse(request.records().size(), created, updated, unchanged, rejected);
  }

  @Override
  @Transactional(readOnly = true)
  public List<HealthSyncStatusResponse> status(UUID userId) {
    if (!users.existsById(userId)) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    List<HealthSyncStatusResponse> result = new ArrayList<>();
    for (HealthProvider provider : HealthProvider.values()) {
      for (HealthDataType dataType : HealthDataType.values()) {
        List<HealthSyncStatusResponse> rows = jdbc.query("""
            SELECT MAX(end_time) AS last_end_time, MAX(synced_at) AS last_synced_at
            FROM health_sync_logs
            WHERE user_id = :userId AND provider = :provider AND data_type = :dataType
            """,
            new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("provider", provider.name())
                .addValue("dataType", dataType.name()),
            (rs, rowNum) -> new HealthSyncStatusResponse(
                provider, dataType,
                rs.getTimestamp("last_end_time") == null
                    ? null : rs.getTimestamp("last_end_time").toInstant(),
                rs.getTimestamp("last_synced_at") == null
                    ? null : rs.getTimestamp("last_synced_at").toInstant()));
        result.add(rows.getFirst());
      }
    }
    return result;
  }

  private MapSqlParameterSource parameters(UUID userId, HealthProvider provider,
      HealthSyncRecordRequest record) {
    return new MapSqlParameterSource()
        .addValue("userId", userId)
        .addValue("provider", provider.name())
        .addValue("dataType", record.dataType().name())
        .addValue("externalId", record.externalId())
        .addValue("value", record.value())
        .addValue("valueMin", record.valueMin())
        .addValue("valueMax", record.valueMax())
        .addValue("dataOrigin", record.dataOrigin())
        .addValue("startTime", Timestamp.from(record.startTime()))
        .addValue("endTime", Timestamp.from(record.endTime()));
  }

  private ExistingRecord findExisting(UUID userId, HealthProvider provider,
      HealthDataType dataType, String externalId) {
    return jdbc.query("""
        SELECT value, value_min, value_max, data_origin, start_time, end_time
        FROM health_sync_logs
        WHERE user_id = :userId AND provider = :provider
          AND data_type = :dataType AND external_id = :externalId
        """,
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("provider", provider.name())
            .addValue("dataType", dataType.name())
            .addValue("externalId", externalId),
        (rs, rowNum) -> new ExistingRecord(
            rs.getBigDecimal("value"), rs.getBigDecimal("value_min"),
            rs.getBigDecimal("value_max"), rs.getString("data_origin"),
            rs.getTimestamp("start_time").toInstant(), rs.getTimestamp("end_time").toInstant()))
        .stream().findFirst().orElse(null);
  }

  private boolean same(ExistingRecord old, HealthSyncRecordRequest current) {
    return old.value().compareTo(current.value()) == 0
        && equal(old.valueMin(), current.valueMin())
        && equal(old.valueMax(), current.valueMax())
        && java.util.Objects.equals(old.dataOrigin(), current.dataOrigin())
        && old.startTime().equals(current.startTime())
        && old.endTime().equals(current.endTime());
  }

  private boolean equal(BigDecimal left, BigDecimal right) {
    return left == null ? right == null : right != null && left.compareTo(right) == 0;
  }

  private String validate(HealthSyncRecordRequest record) {
    Instant now = clock.instant();
    if (record.endTime().isBefore(record.startTime())) return "endTime must not be before startTime";
    if (Duration.between(record.startTime(), record.endTime()).compareTo(Duration.ofHours(24)) > 0) {
      return "bucket must not be longer than 24 hours";
    }
    if (record.startTime().isBefore(now.minus(Duration.ofDays(properties.syncBackfillDays())))
        || record.startTime().isAfter(now.plus(properties.futureTolerance()))) {
      return ErrorCode.TIMESTAMP_OUT_OF_RANGE.name();
    }
    if (record.dataType() == HealthDataType.STEPS) {
      if (record.value().stripTrailingZeros().scale() > 0
          || record.value().compareTo(BigDecimal.ZERO) < 0
          || record.value().compareTo(MAX_STEPS) > 0) {
        return "steps must be an integer between 0 and 100000";
      }
      return null;
    }
    if (record.value().compareTo(MIN_HEART_RATE) < 0
        || record.value().compareTo(MAX_HEART_RATE) > 0) {
      return "heart rate must be between 20 and 260 bpm";
    }
    if (record.valueMin() != null && record.valueMax() != null
        && record.valueMin().compareTo(record.valueMax()) > 0) {
      return "minimum heart rate must not exceed maximum heart rate";
    }
    if (record.valueMin() != null && record.value().compareTo(record.valueMin()) < 0) {
      return "heart rate must be at least the minimum";
    }
    if (record.valueMax() != null && record.value().compareTo(record.valueMax()) > 0) {
      return "heart rate must not exceed the maximum";
    }
    return null;
  }

  private void publishChangedEvents(UUID userId, List<ValidRecord> records) {
    ZoneId zone = timeResolver.zoneOf(userId);
    Set<LocalDate> dates = records.stream()
        .map(item -> item.record().startTime().atZone(zone).toLocalDate())
        .collect(java.util.stream.Collectors.toSet());
    dates.forEach(date -> events.publishEvent(new ActivityDataChangedEvent(userId, date)));
  }

  private record ValidRecord(int index, HealthSyncRecordRequest record) {}
  private record ExistingRecord(BigDecimal value, BigDecimal valueMin, BigDecimal valueMax,
      String dataOrigin, Instant startTime, Instant endTime) {}
}
