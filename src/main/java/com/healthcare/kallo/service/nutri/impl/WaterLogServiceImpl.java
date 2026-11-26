package com.healthcare.kallo.service.nutri.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.request.nutri.CreateWaterLogRequest;
import com.healthcare.kallo.dto.response.nutri.DailyWaterLogsResponse;
import com.healthcare.kallo.dto.response.nutri.WaterLogResponse;
import com.healthcare.kallo.event.sys.WaterLoggedEvent;
import com.healthcare.kallo.model.nutri.DailySummary;
import com.healthcare.kallo.model.nutri.WaterLog;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.repository.nutri.DailySummaryRepository;
import com.healthcare.kallo.repository.nutri.WaterLogRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.act.UserTimeResolver;
import com.healthcare.kallo.service.nutri.DailySummaryService;
import com.healthcare.kallo.service.nutri.WaterLogService;
import com.healthcare.kallo.service.sys.sync.SyncApplyResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WaterLogServiceImpl implements WaterLogService {

  private static final int MIN_WATER_ML = 1;
  private static final int MAX_WATER_ML = 5000;
  private static final int MAX_NOTE_LENGTH = 255;

  private final WaterLogRepository waterLogRepository;
  private final DailySummaryRepository dailySummaryRepository;
  private final UserHealthProfileRepository profileRepository;
  private final DailySummaryService dailySummaryService;
  private final UserTimeResolver userTimeResolver;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public WaterLogServiceImpl(
      WaterLogRepository waterLogRepository,
      DailySummaryRepository dailySummaryRepository,
      UserHealthProfileRepository profileRepository,
      DailySummaryService dailySummaryService,
      UserTimeResolver userTimeResolver,
      ApplicationEventPublisher events,
      Clock clock) {
    this.waterLogRepository = waterLogRepository;
    this.dailySummaryRepository = dailySummaryRepository;
    this.profileRepository = profileRepository;
    this.dailySummaryService = dailySummaryService;
    this.userTimeResolver = userTimeResolver;
    this.events = events;
    this.clock = clock;
  }

  @Override
  @Transactional
  public CreateResult logWater(UUID userId, CreateWaterLogRequest request) {
    if (request == null || request.clientUuid() == null) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
    validateAmount(request.amountMl());
    ZoneId zone = userTimeResolver.zoneOf(userId);

    Optional<WaterLog> existingOpt =
        waterLogRepository.findByUserIdAndClientUuid(userId, request.clientUuid());
    if (existingOpt.isPresent()) {
      WaterLog existing = existingOpt.get();
      if (existing.getDeletedAt() != null) {
        throw new BusinessException(ErrorCode.RECORD_DELETED);
      }
      return new CreateResult(toResponse(existing), false);
    }

    Instant now = Instant.now(clock);
    LocalDate today = userTimeResolver.localDate(now, zone);
    Instant loggedAt = request.loggedAt() != null ? request.loggedAt() : now;
    LocalDate loggedDate =
        request.loggedDate() != null
            ? request.loggedDate()
            : userTimeResolver.localDate(loggedAt, zone);
    if (loggedDate.isAfter(today)) {
      throw new BusinessException(ErrorCode.FUTURE_DATE_NOT_ALLOWED);
    }

    String note = normalizeNote(request.note());
    int inserted =
        waterLogRepository.insertIfAbsent(
            UUID.randomUUID(),
            userId,
            request.amountMl(),
            loggedDate,
            loggedAt,
            note,
            request.clientUuid(),
            now,
            now);

    if (inserted == 0) {
      WaterLog existing =
          waterLogRepository
              .findByUserIdAndClientUuid(userId, request.clientUuid())
              .orElseThrow(() -> new BusinessException(ErrorCode.WATER_LOG_NOT_FOUND));
      if (existing.getDeletedAt() != null) {
        throw new BusinessException(ErrorCode.RECORD_DELETED);
      }
      return new CreateResult(toResponse(existing), false);
    }

    WaterLog saved =
        waterLogRepository
            .findByUserIdAndClientUuid(userId, request.clientUuid())
            .orElseThrow(() -> new BusinessException(ErrorCode.WATER_LOG_NOT_FOUND));
    dailySummaryService.recalculate(userId, loggedDate);
    events.publishEvent(new WaterLoggedEvent(userId, loggedAt));
    return new CreateResult(toResponse(saved), true);
  }

  @Override
  @Transactional(readOnly = true)
  public DailyWaterLogsResponse getByDate(UUID userId, LocalDate date) {
    ZoneId zone = userTimeResolver.zoneOf(userId);
    LocalDate targetDate =
        date != null ? date : userTimeResolver.localDate(Instant.now(clock), zone);

    List<WaterLog> logs =
        waterLogRepository.findByUserIdAndLoggedDateAndDeletedAtIsNullOrderByLoggedAtAsc(
            userId, targetDate);
    int totalWaterMl =
        logs.stream().mapToInt(w -> w.getAmountMl() != null ? w.getAmountMl() : 0).sum();
    Integer targetWaterMl = resolveTargetWaterMl(userId, targetDate);

    return new DailyWaterLogsResponse(
        targetDate, totalWaterMl, targetWaterMl, logs.stream().map(this::toResponse).toList());
  }

  @Override
  @Transactional
  public void delete(UUID userId, UUID id) {
    userTimeResolver.zoneOf(userId);
    WaterLog existing =
        waterLogRepository
            .findByIdAndUserId(id, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.WATER_LOG_NOT_FOUND));
    if (existing.getDeletedAt() != null) {
      return;
    }

    Instant now = Instant.now(clock);
    existing.setDeletedAt(now);
    existing.setUpdatedAt(now);
    waterLogRepository.saveAndFlush(existing);

    if (existing.getLoggedDate() != null) {
      dailySummaryService.recalculate(userId, existing.getLoggedDate());
    }
  }

  @Override
  @Transactional
  public SyncApplyResult upsertFromSync(
      UUID userId, UUID clientUuid, Instant opUpdatedAt, Map<String, Object> data) {
    if (data == null) {
      throw new IllegalArgumentException("WATER_LOG upsert requires data payload");
    }

    Instant normalizedUpdatedAt = opUpdatedAt.truncatedTo(ChronoUnit.MICROS);
    ZoneId zone = userTimeResolver.zoneOf(userId);
    Integer amountMl = parseAmountRequired(data);
    String note = normalizeNote(parseStringOptional(data, "note"));
    Instant loggedAt = parseInstantOptional(data, "loggedAt", "logged_at");
    LocalDate loggedDate = parseDateOptional(data, "loggedDate", "logged_date");
    if (loggedAt == null && loggedDate == null) {
      throw new IllegalArgumentException("WATER_LOG upsert requires loggedAt or loggedDate");
    }
    if (loggedAt == null) {
      loggedAt = userTimeResolver.dayRange(loggedDate, zone).start();
    }
    Instant normalizedLoggedAt = loggedAt.truncatedTo(ChronoUnit.MICROS);
    if (loggedDate == null) {
      loggedDate = userTimeResolver.localDate(normalizedLoggedAt, zone);
    }

    Optional<WaterLog> existingOpt =
        waterLogRepository.findByUserIdAndClientUuid(userId, clientUuid);
    if (existingOpt.isEmpty()) {
      Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
      waterLogRepository.insertIfAbsent(
          UUID.randomUUID(),
          userId,
          amountMl,
          loggedDate,
          normalizedLoggedAt,
          note,
          clientUuid,
          now,
          normalizedUpdatedAt);
      dailySummaryService.recalculate(userId, loggedDate);
      events.publishEvent(new WaterLoggedEvent(userId, normalizedLoggedAt));
      return SyncApplyResult.applied();
    }

    WaterLog existing = existingOpt.get();
    Instant existingUpdatedAt = existing.getUpdatedAt().truncatedTo(ChronoUnit.MICROS);
    if (existingUpdatedAt.isAfter(normalizedUpdatedAt)) {
      return SyncApplyResult.conflictServerWins(toSyncData(existing), existing.getUpdatedAt());
    }
    if (existingUpdatedAt.equals(normalizedUpdatedAt)) {
      Instant existingLoggedAt =
          existing.getLoggedAt() != null
              ? existing.getLoggedAt().truncatedTo(ChronoUnit.MICROS)
              : null;
      if (existing.getDeletedAt() == null
          && Objects.equals(existing.getAmountMl(), amountMl)
          && Objects.equals(existing.getLoggedDate(), loggedDate)
          && Objects.equals(existingLoggedAt, normalizedLoggedAt)
          && Objects.equals(existing.getNote(), note)) {
        return SyncApplyResult.unchanged();
      }
      return SyncApplyResult.conflictServerWins(toSyncData(existing), existing.getUpdatedAt());
    }

    LocalDate previousDate = existing.getLoggedDate();
    existing.setAmountMl(amountMl);
    existing.setLoggedDate(loggedDate);
    existing.setLoggedAt(normalizedLoggedAt);
    existing.setNote(note);
    existing.setDeletedAt(null);
    existing.setUpdatedAt(normalizedUpdatedAt);
    waterLogRepository.saveAndFlush(existing);

    dailySummaryService.recalculate(userId, loggedDate);
    if (previousDate != null && !previousDate.equals(loggedDate)) {
      dailySummaryService.recalculate(userId, previousDate);
    }
    events.publishEvent(new WaterLoggedEvent(userId, normalizedLoggedAt));
    return SyncApplyResult.applied();
  }

  @Override
  @Transactional
  public SyncApplyResult softDeleteFromSync(UUID userId, UUID clientUuid, Instant opUpdatedAt) {
    userTimeResolver.zoneOf(userId);
    Instant normalizedUpdatedAt = opUpdatedAt.truncatedTo(ChronoUnit.MICROS);
    Optional<WaterLog> existingOpt =
        waterLogRepository.findByUserIdAndClientUuid(userId, clientUuid);
    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);

    if (existingOpt.isEmpty()) {
      waterLogRepository.insertTombstoneIfAbsent(
          UUID.randomUUID(), userId, clientUuid, normalizedUpdatedAt, now);
      return SyncApplyResult.applied();
    }

    WaterLog existing = existingOpt.get();
    if (existing.getUpdatedAt().truncatedTo(ChronoUnit.MICROS).isAfter(normalizedUpdatedAt)) {
      return SyncApplyResult.conflictServerWins(toSyncData(existing), existing.getUpdatedAt());
    }
    if (existing.getDeletedAt() != null) {
      return SyncApplyResult.unchanged();
    }

    LocalDate affectedDate = existing.getLoggedDate();
    existing.setDeletedAt(now);
    existing.setUpdatedAt(normalizedUpdatedAt);
    waterLogRepository.saveAndFlush(existing);
    if (affectedDate != null) {
      dailySummaryService.recalculate(userId, affectedDate);
    }
    return SyncApplyResult.applied();
  }

  private void validateAmount(Integer amountMl) {
    if (amountMl == null || amountMl < MIN_WATER_ML || amountMl > MAX_WATER_ML) {
      throw new BusinessException(ErrorCode.INVALID_WATER_AMOUNT);
    }
  }

  private Integer resolveTargetWaterMl(UUID userId, LocalDate date) {
    Optional<DailySummary> summaryOpt =
        dailySummaryRepository.findByUserIdAndSummaryDate(userId, date);
    if (summaryOpt.isPresent()) {
      return summaryOpt.get().getTargetWaterMl();
    }
    UserHealthProfile profile = profileRepository.findById(userId).orElse(null);
    if (profile == null
        || profile.getWaterLiters() == null
        || profile.getWaterLiters().compareTo(BigDecimal.ZERO) <= 0) {
      return null;
    }
    return profile
        .getWaterLiters()
        .multiply(BigDecimal.valueOf(1000))
        .setScale(0, RoundingMode.HALF_UP)
        .intValue();
  }

  private String normalizeNote(String note) {
    if (note == null) {
      return null;
    }
    String trimmed = note.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    if (trimmed.length() > MAX_NOTE_LENGTH) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
    return trimmed;
  }

  private WaterLogResponse toResponse(WaterLog log) {
    return new WaterLogResponse(
        log.getId(),
        log.getAmountMl(),
        log.getLoggedDate(),
        log.getLoggedAt(),
        log.getNote(),
        log.getClientUuid(),
        log.getCreatedAt(),
        log.getUpdatedAt());
  }

  private Map<String, Object> toSyncData(WaterLog entry) {
    Map<String, Object> map = new LinkedHashMap<>();
    if (entry.getAmountMl() != null) map.put("amountMl", entry.getAmountMl());
    if (entry.getLoggedDate() != null) map.put("loggedDate", entry.getLoggedDate().toString());
    if (entry.getLoggedAt() != null) map.put("loggedAt", entry.getLoggedAt().toString());
    if (entry.getNote() != null) map.put("note", entry.getNote());
    if (entry.getDeletedAt() != null) map.put("deletedAt", entry.getDeletedAt().toString());
    return map;
  }

  private static Integer parseAmountRequired(Map<String, Object> data) {
    Object val = data.containsKey("amountMl") ? data.get("amountMl") : data.get("amount_ml");
    if (val == null) {
      throw new IllegalArgumentException("Missing required field amountMl");
    }
    int amount = val instanceof Number n ? n.intValue() : Integer.parseInt(val.toString());
    if (amount < MIN_WATER_ML || amount > MAX_WATER_ML) {
      throw new BusinessException(ErrorCode.INVALID_WATER_AMOUNT);
    }
    return amount;
  }

  private static String parseStringOptional(Map<String, Object> data, String key) {
    Object val = data.get(key);
    return val != null ? val.toString() : null;
  }

  private static Instant parseInstantOptional(Map<String, Object> data, String... keys) {
    for (String key : keys) {
      Object val = data.get(key);
      if (val != null) {
        return val instanceof Instant instant ? instant : Instant.parse(val.toString());
      }
    }
    return null;
  }

  private static LocalDate parseDateOptional(Map<String, Object> data, String... keys) {
    for (String key : keys) {
      Object val = data.get(key);
      if (val != null) {
        return val instanceof LocalDate ld ? ld : LocalDate.parse(val.toString());
      }
    }
    return null;
  }
}
