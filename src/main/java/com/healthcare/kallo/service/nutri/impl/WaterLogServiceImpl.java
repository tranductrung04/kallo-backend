package com.healthcare.kallo.service.nutri.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.request.nutri.CreateWaterLogRequest;
import com.healthcare.kallo.dto.response.nutri.DailyWaterResponse;
import com.healthcare.kallo.dto.response.nutri.WaterLogResponse;
import com.healthcare.kallo.event.sys.WaterLoggedEvent;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.nutri.WaterLog;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.nutri.WaterLogRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.nutri.WaterLogService;
import com.healthcare.kallo.service.report.DailySummaryService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WaterLogServiceImpl implements WaterLogService {

  private final WaterLogRepository waterLogRepository;
  private final UserRepository userRepository;
  private final UserHealthProfileRepository userHealthProfileRepository;
  private final DailySummaryService dailySummaryService;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public WaterLogServiceImpl(
      WaterLogRepository waterLogRepository,
      UserRepository userRepository,
      UserHealthProfileRepository userHealthProfileRepository,
      DailySummaryService dailySummaryService,
      ApplicationEventPublisher eventPublisher,
      Clock clock) {
    this.waterLogRepository = waterLogRepository;
    this.userRepository = userRepository;
    this.userHealthProfileRepository = userHealthProfileRepository;
    this.dailySummaryService = dailySummaryService;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  @Override
  @Transactional
  public WaterLogResponse logWater(UUID userId, CreateWaterLogRequest request) {
    // 1. Idempotency check: if clientUuid already exists for this user, return existing
    Optional<WaterLog> existing = waterLogRepository.findByUserIdAndClientUuid(userId, request.clientUuid());
    if (existing.isPresent()) {
      return toResponse(existing.get());
    }

    User user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    Instant loggedAt = request.loggedAt() != null ? request.loggedAt() : Instant.now(clock);

    WaterLog log = new WaterLog();
    log.setUser(user);
    log.setAmountMl(request.amountMl());
    log.setLoggedDate(request.loggedDate());
    log.setLoggedAt(loggedAt);
    log.setClientUuid(request.clientUuid());
    log.setNote(request.note());

    WaterLog saved = waterLogRepository.save(log);

    // 2. Publish event & recalculate daily summary
    eventPublisher.publishEvent(new WaterLoggedEvent(userId, loggedAt));
    dailySummaryService.recalculate(userId, request.loggedDate());

    return toResponse(saved);
  }

  @Override
  @Transactional
  public void deleteWaterLog(UUID userId, UUID logId) {
    WaterLog log = waterLogRepository.findByIdAndUserId(logId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.WATER_LOG_NOT_FOUND));

    LocalDate date = log.getLoggedDate();
    waterLogRepository.delete(log);
    dailySummaryService.recalculate(userId, date);
  }

  @Override
  @Transactional(readOnly = true)
  public DailyWaterResponse getDailyWater(UUID userId, LocalDate date) {
    List<WaterLog> logs = waterLogRepository.findByUserIdAndLoggedDateOrderByLoggedAtAsc(userId, date);
    int total = logs.stream().mapToInt(WaterLog::getAmountMl).sum();
    int target = userHealthProfileRepository.findByUserId(userId)
        .map(UserHealthProfile::getWaterGoalMl)
        .orElse(2000);

    List<WaterLogResponse> responses = logs.stream().map(this::toResponse).toList();
    return new DailyWaterResponse(date, total, target, responses);
  }

  private WaterLogResponse toResponse(WaterLog log) {
    return new WaterLogResponse(
        log.getId(),
        log.getAmountMl(),
        log.getLoggedDate(),
        log.getLoggedAt(),
        log.getClientUuid(),
        log.getNote()
    );
  }
}
