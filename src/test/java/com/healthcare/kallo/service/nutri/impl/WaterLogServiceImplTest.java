package com.healthcare.kallo.service.nutri.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.request.nutri.CreateWaterLogRequest;
import com.healthcare.kallo.event.sys.WaterLoggedEvent;
import com.healthcare.kallo.model.nutri.WaterLog;
import com.healthcare.kallo.repository.nutri.DailySummaryRepository;
import com.healthcare.kallo.repository.nutri.WaterLogRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.act.UserTimeResolver;
import com.healthcare.kallo.service.nutri.DailySummaryService;
import com.healthcare.kallo.service.nutri.WaterLogService;
import com.healthcare.kallo.service.sys.sync.SyncApplyResult;
import com.healthcare.kallo.service.sys.sync.SyncApplyStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WaterLogServiceImplTest {

  private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final Instant FIXED_NOW = Instant.parse("2026-11-25T05:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

  @Mock private WaterLogRepository waterLogRepository;
  @Mock private DailySummaryRepository dailySummaryRepository;
  @Mock private UserHealthProfileRepository profileRepository;
  @Mock private DailySummaryService dailySummaryService;
  @Mock private UserTimeResolver userTimeResolver;
  @Mock private ApplicationEventPublisher events;

  private WaterLogServiceImpl service;
  private UUID userId;

  @BeforeEach
  void setUp() {
    service =
        new WaterLogServiceImpl(
            waterLogRepository,
            dailySummaryRepository,
            profileRepository,
            dailySummaryService,
            userTimeResolver,
            events,
            Clock.fixed(FIXED_NOW, VN_ZONE));
    userId = UUID.randomUUID();
    lenient().when(userTimeResolver.zoneOf(userId)).thenReturn(VN_ZONE);
    lenient().when(userTimeResolver.localDate(FIXED_NOW, VN_ZONE)).thenReturn(TODAY);
  }

  @Test
  void logWaterInsertsRecalculatesAndPublishesEvent() {
    UUID clientUuid = UUID.randomUUID();
    WaterLog saved = waterLog(clientUuid, 250);
    when(waterLogRepository.findByUserIdAndClientUuid(userId, clientUuid))
        .thenReturn(Optional.empty(), Optional.of(saved));
    when(waterLogRepository.insertIfAbsent(
            any(), eq(userId), eq(250), eq(TODAY), eq(FIXED_NOW), eq("Morning"), eq(clientUuid), eq(FIXED_NOW), eq(FIXED_NOW)))
        .thenReturn(1);

    WaterLogService.CreateResult result =
        service.logWater(
            userId, new CreateWaterLogRequest(250, TODAY, FIXED_NOW, clientUuid, " Morning "));

    assertThat(result.created()).isTrue();
    assertThat(result.response().amountMl()).isEqualTo(250);
    verify(dailySummaryService).recalculate(userId, TODAY);
    verify(events).publishEvent(new WaterLoggedEvent(userId, FIXED_NOW));
  }

  @Test
  void logWaterValidatesAmountAndFutureDate() {
    assertThatThrownBy(
            () ->
                service.logWater(
                    userId,
                    new CreateWaterLogRequest(0, TODAY, FIXED_NOW, UUID.randomUUID(), null)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_WATER_AMOUNT);

    assertThatThrownBy(
            () ->
                service.logWater(
                    userId,
                    new CreateWaterLogRequest(
                        300, TODAY.plusDays(1), FIXED_NOW, UUID.randomUUID(), null)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.FUTURE_DATE_NOT_ALLOWED);
  }

  @Test
  void syncDeleteWritesTombstoneWhenMissingAndBlocksOlderUpsert() {
    UUID clientUuid = UUID.randomUUID();
    when(waterLogRepository.findByUserIdAndClientUuid(userId, clientUuid))
        .thenReturn(Optional.empty());

    SyncApplyResult deleteRes =
        service.softDeleteFromSync(userId, clientUuid, FIXED_NOW.plusSeconds(10));
    assertThat(deleteRes.status()).isEqualTo(SyncApplyStatus.APPLIED);
    verify(waterLogRepository)
        .insertTombstoneIfAbsent(any(), eq(userId), eq(clientUuid), eq(FIXED_NOW.plusSeconds(10)), eq(FIXED_NOW));

    WaterLog tombstone = waterLog(clientUuid, null);
    tombstone.setDeletedAt(FIXED_NOW);
    tombstone.setUpdatedAt(FIXED_NOW.plusSeconds(10));
    when(waterLogRepository.findByUserIdAndClientUuid(userId, clientUuid))
        .thenReturn(Optional.of(tombstone));

    SyncApplyResult staleUpsert =
        service.upsertFromSync(
            userId,
            clientUuid,
            FIXED_NOW,
            Map.of("amountMl", 300, "loggedAt", FIXED_NOW.toString()));

    assertThat(staleUpsert.status()).isEqualTo(SyncApplyStatus.CONFLICT_SERVER_WINS);
    verify(waterLogRepository, never()).saveAndFlush(any());
  }

  private WaterLog waterLog(UUID clientUuid, Integer amount) {
    WaterLog log = new WaterLog();
    ReflectionTestUtils.setField(log, "id", UUID.randomUUID());
    log.setUserId(userId);
    log.setAmountMl(amount);
    log.setLoggedDate(TODAY);
    log.setLoggedAt(FIXED_NOW);
    log.setNote("Morning");
    log.setClientUuid(clientUuid);
    log.setCreatedAt(FIXED_NOW);
    log.setUpdatedAt(FIXED_NOW);
    return log;
  }
}
