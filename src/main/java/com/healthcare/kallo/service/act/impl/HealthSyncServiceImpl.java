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
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
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
  private static final int MAX_DECIMAL_INTEGER_DIGITS = 10;

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
    if (request.records().size() > properties.maxSyncBatchSize()) {
      throw new BusinessException(ErrorCode.SYNC_BATCH_TOO_LARGE);
    }
    if (!users.existsById(userId)) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }

    List<HealthSyncResponse.RejectedRecord> rejected = new ArrayList<>();
    List<ValidRecord> valid = new ArrayList<>();
    Set<String> batchKeys = new HashSet<>();
    for (int i = 0; i < request.records().size(); i++) {
      HealthSyncRecordRequest record = request.records().get(i);
      String magnitudeError = numericMagnitudeError(record);
      HealthSyncRecordRequest normalized = record == null ? null : normalize(record);
      String reason = magnitudeError != null ? magnitudeError : validate(normalized);
      String key = normalized == null || normalized.dataType() == null || normalized.externalId() == null
          ? null : normalized.dataType().name() + "\u0000" + normalized.externalId();
      if (reason == null && key != null && !batchKeys.add(key)) {
        reason = "duplicate externalId in request";
      }
      if (reason == null) {
        valid.add(new ValidRecord(normalized));
      } else {
        rejected.add(new HealthSyncResponse.RejectedRecord(i, reason));
      }
    }

    valid.sort(Comparator.comparing(item -> item.record().externalId()));
    Instant syncedAt = clock.instant();
    List<ChangedRecord> changed = writeChangedRecords(userId, request.provider(), valid, syncedAt);
    touchLastSyncedAt(userId, request.provider(), changed, syncedAt);
    publishChangedEvents(userId, changed);

    int created = (int) changed.stream().filter(ChangedRecord::inserted).count();
    int updated = changed.size() - created;
    int unchanged = valid.size() - changed.size();
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
    return jdbc.query("""
        SELECT provider, data_type, MAX(end_time) AS last_end_time,
               MAX(last_synced_at) AS last_synced_at
        FROM health_sync_logs
        WHERE user_id = :userId
        GROUP BY provider, data_type
        ORDER BY provider, data_type
        """, new MapSqlParameterSource("userId", userId), (rs, rowNum) ->
        new HealthSyncStatusResponse(
            HealthProvider.valueOf(rs.getString("provider")),
            HealthDataType.valueOf(rs.getString("data_type")),
            rs.getTimestamp("last_end_time").toInstant(),
            rs.getTimestamp("last_synced_at").toInstant()));
  }

  private List<ChangedRecord> writeChangedRecords(UUID userId, HealthProvider provider,
      List<ValidRecord> records, Instant syncedAt) {
    if (records.isEmpty()) {
      return List.of();
    }
    StringBuilder values = new StringBuilder();
    MapSqlParameterSource params = new MapSqlParameterSource("userId", userId)
        .addValue("provider", provider.name()).addValue("syncedAt", Timestamp.from(syncedAt));
    for (int i = 0; i < records.size(); i++) {
      if (i > 0) values.append(", ");
      HealthSyncRecordRequest record = records.get(i).record();
      values.append("(:dataType").append(i).append(", :externalId").append(i)
          .append(", :value").append(i).append(", :valueMin").append(i)
          .append(", :valueMax").append(i).append(", :dataOrigin").append(i)
          .append(", :startTime").append(i).append(", :endTime").append(i).append(")");
      params.addValue("dataType" + i, record.dataType().name())
          .addValue("externalId" + i, record.externalId())
          .addValue("value" + i, record.value())
          .addValue("valueMin" + i, record.valueMin())
          .addValue("valueMax" + i, record.valueMax())
          .addValue("dataOrigin" + i, record.dataOrigin())
          .addValue("startTime" + i, Timestamp.from(record.startTime()))
          .addValue("endTime" + i, Timestamp.from(record.endTime()));
    }

    String sql = """
        WITH input(data_type, external_id, value, value_min, value_max, data_origin, start_time, end_time) AS (
          VALUES %s
        ),
        existing AS (
          SELECT h.data_type, h.external_id, h.start_time AS old_start_time
          FROM health_sync_logs h
          JOIN input i ON i.data_type = h.data_type AND i.external_id = h.external_id
          WHERE h.user_id = :userId AND h.provider = :provider
        ),
        changed AS (
          INSERT INTO health_sync_logs
            (user_id, provider, data_type, external_id, value, value_min, value_max,
             data_origin, start_time, end_time, synced_at, last_synced_at)
          SELECT :userId, :provider, data_type::varchar, external_id::varchar,
                 value::numeric, value_min::numeric, value_max::numeric,
                 data_origin::varchar, start_time::timestamptz, end_time::timestamptz,
                 :syncedAt, :syncedAt
          FROM input
          ON CONFLICT (user_id, provider, data_type, external_id) DO UPDATE SET
            value = EXCLUDED.value, value_min = EXCLUDED.value_min,
            value_max = EXCLUDED.value_max, data_origin = EXCLUDED.data_origin,
            start_time = EXCLUDED.start_time, end_time = EXCLUDED.end_time,
            synced_at = :syncedAt
          WHERE health_sync_logs.value IS DISTINCT FROM EXCLUDED.value
             OR health_sync_logs.value_min IS DISTINCT FROM EXCLUDED.value_min
             OR health_sync_logs.value_max IS DISTINCT FROM EXCLUDED.value_max
             OR health_sync_logs.data_origin IS DISTINCT FROM EXCLUDED.data_origin
             OR health_sync_logs.start_time IS DISTINCT FROM EXCLUDED.start_time
             OR health_sync_logs.end_time IS DISTINCT FROM EXCLUDED.end_time
          RETURNING data_type, external_id, start_time, (xmax = 0) AS inserted
        )
        SELECT c.data_type, c.external_id, c.start_time, c.inserted, e.old_start_time
        FROM changed c
        LEFT JOIN existing e ON e.data_type = c.data_type AND e.external_id = c.external_id
        """.formatted(values);

    return jdbc.query(sql, params, (rs, rowNum) -> new ChangedRecord(
        HealthDataType.valueOf(rs.getString("data_type")),
        rs.getString("external_id"),
        rs.getTimestamp("start_time").toInstant(),
        rs.getTimestamp("old_start_time") == null ? null : rs.getTimestamp("old_start_time").toInstant(),
        rs.getBoolean("inserted")));
  }

  private void touchLastSyncedAt(UUID userId, HealthProvider provider, List<ChangedRecord> records,
      Instant syncedAt) {
    if (records.isEmpty()) return;
    MapSqlParameterSource params = new MapSqlParameterSource("userId", userId)
        .addValue("provider", provider.name()).addValue("syncedAt", Timestamp.from(syncedAt));
    StringBuilder values = new StringBuilder();
    for (int i = 0; i < records.size(); i++) {
      if (i > 0) values.append(", ");
      values.append("(:touchType").append(i).append(", :touchExternalId").append(i).append(")");
      params.addValue("touchType" + i, records.get(i).dataType().name())
          .addValue("touchExternalId" + i, records.get(i).externalId());
    }
    jdbc.update("""
        UPDATE health_sync_logs h
        SET last_synced_at = :syncedAt
        FROM (VALUES %s) AS i(data_type, external_id)
        WHERE h.user_id = :userId AND h.provider = :provider
          AND h.data_type = i.data_type AND h.external_id = i.external_id
        """.formatted(values), params);
  }

  private String validate(HealthSyncRecordRequest record) {
    if (record == null) return "record is required";
    if (record.dataType() == null) return "dataType is required";
    if (record.externalId() == null || record.externalId().isBlank()
        || record.externalId().length() > 100) return "externalId is required and must be at most 100 characters";
    if (record.startTime() == null || record.endTime() == null) return "startTime and endTime are required";
    if (record.value() == null) return "value is required";
    if (record.dataOrigin() != null && record.dataOrigin().length() > 100) return "dataOrigin is too long";
    Instant now = clock.instant();
    if (record.endTime().isBefore(record.startTime())) return "endTime must not be before startTime";
    if (Duration.between(record.startTime(), record.endTime()).compareTo(Duration.ofHours(24)) > 0) {
      return "bucket must not be longer than 24 hours";
    }
    if (record.startTime().isBefore(now.minus(Duration.ofDays(properties.syncBackfillDays())))
        || record.startTime().isAfter(now.plus(properties.futureTolerance()))
        || record.endTime().isAfter(now.plus(properties.futureTolerance()))) {
      return "timestamp is out of range";
    }
    if (record.value().precision() - record.value().scale() > MAX_DECIMAL_INTEGER_DIGITS) {
      return "value exceeds NUMERIC(12,2)";
    }
    if (record.dataType() == HealthDataType.STEPS) {
      if (record.valueMin() != null || record.valueMax() != null) return "steps do not support min or max";
      if (record.value().stripTrailingZeros().scale() > 0
          || record.value().compareTo(BigDecimal.ZERO) < 0
          || record.value().compareTo(MAX_STEPS) > 0) {
        return "steps must be an integer between 0 and 100000";
      }
      return null;
    }
    if (!inHeartRateRange(record.value()) || !inHeartRateRange(record.valueMin())
        || !inHeartRateRange(record.valueMax())) return "heart rate values must be between 20 and 260 bpm";
    if (record.valueMin() != null && record.valueMax() != null
        && record.valueMin().compareTo(record.valueMax()) > 0) return "minimum heart rate must not exceed maximum heart rate";
    if (record.valueMin() != null && record.value().compareTo(record.valueMin()) < 0) return "heart rate must be at least the minimum";
    if (record.valueMax() != null && record.value().compareTo(record.valueMax()) > 0) return "heart rate must not exceed the maximum";
    return null;
  }

  private boolean inHeartRateRange(BigDecimal value) {
    return value == null || (value.compareTo(MIN_HEART_RATE) >= 0
        && value.compareTo(MAX_HEART_RATE) <= 0);
  }

  private HealthSyncRecordRequest normalize(HealthSyncRecordRequest record) {
    return new HealthSyncRecordRequest(record.dataType(), record.externalId(), record.startTime(),
        record.endTime(), round(record.value()), round(record.valueMin()), round(record.valueMax()),
        record.dataOrigin());
  }

  private String numericMagnitudeError(HealthSyncRecordRequest record) {
    if (record == null) return null;
    for (BigDecimal value : new BigDecimal[] {record.value(), record.valueMin(), record.valueMax()}) {
      if (value != null && (value.precision() - value.scale() > MAX_DECIMAL_INTEGER_DIGITS
          || value.scale() > 10_000)) {
        return "value exceeds NUMERIC(12,2)";
      }
    }
    return null;
  }

  private BigDecimal round(BigDecimal value) {
    return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
  }

  private void publishChangedEvents(UUID userId, List<ChangedRecord> changed) {
    ZoneId zone = timeResolver.zoneOf(userId);
    Set<LocalDate> dates = changed.stream()
        .flatMap(item -> java.util.stream.Stream.of(item.oldStartTime(), item.startTime()))
        .filter(Objects::nonNull)
        .map(instant -> timeResolver.localDate(instant, zone))
        .collect(Collectors.toSet());
    dates.forEach(date -> events.publishEvent(new ActivityDataChangedEvent(userId, date)));
  }

  private record ValidRecord(HealthSyncRecordRequest record) {}
  private record ChangedRecord(HealthDataType dataType, String externalId, Instant startTime,
      Instant oldStartTime, boolean inserted) {}
}
