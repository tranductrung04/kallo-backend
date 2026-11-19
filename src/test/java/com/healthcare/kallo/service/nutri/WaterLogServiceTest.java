package com.healthcare.kallo.service.nutri;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.healthcare.kallo.dto.request.nutri.CreateWaterLogRequest;
import com.healthcare.kallo.dto.response.nutri.DailyWaterResponse;
import com.healthcare.kallo.dto.response.nutri.WaterLogResponse;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.nutri.WaterLog;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.nutri.WaterLogRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.nutri.impl.WaterLogServiceImpl;
import com.healthcare.kallo.service.report.DailySummaryService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class WaterLogServiceTest {

  @Mock private WaterLogRepository waterLogRepository;
  @Mock private UserRepository userRepository;
  @Mock private UserHealthProfileRepository userHealthProfileRepository;
  @Mock private DailySummaryService dailySummaryService;
  @Mock private ApplicationEventPublisher eventPublisher;

  private final Clock clock = Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneId.of("UTC"));
  private WaterLogService waterLogService;

  private UUID userId;
  private User user;

  @BeforeEach
  void setUp() {
    waterLogService = new WaterLogServiceImpl(
        waterLogRepository,
        userRepository,
        userHealthProfileRepository,
        dailySummaryService,
        eventPublisher,
        clock
    );

    userId = UUID.randomUUID();
    user = new User();
    user.setId(userId);
  }

  @Test
  @DisplayName("logWater() saves log, emits event, and recalculates daily summary")
  void logWater_success() {
    UUID clientUuid = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 11, 20);
    CreateWaterLogRequest request = new CreateWaterLogRequest(250, date, null, clientUuid, "Glass of water");

    when(waterLogRepository.findByUserIdAndClientUuid(userId, clientUuid)).thenReturn(Optional.empty());
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(waterLogRepository.save(any(WaterLog.class))).thenAnswer(inv -> {
      WaterLog wl = inv.getArgument(0);
      wl.setId(UUID.randomUUID());
      return wl;
    });

    WaterLogResponse response = waterLogService.logWater(userId, request);

    assertThat(response).isNotNull();
    assertThat(response.amountMl()).isEqualTo(250);
    assertThat(response.loggedDate()).isEqualTo(date);
    verify(waterLogRepository).save(any(WaterLog.class));
    verify(dailySummaryService).recalculate(userId, date);
  }

  @Test
  @DisplayName("logWater() returns existing log when clientUuid is duplicate")
  void logWater_idempotent() {
    UUID clientUuid = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 11, 20);
    WaterLog existing = new WaterLog();
    existing.setId(UUID.randomUUID());
    existing.setAmountMl(300);
    existing.setLoggedDate(date);
    existing.setClientUuid(clientUuid);

    when(waterLogRepository.findByUserIdAndClientUuid(userId, clientUuid)).thenReturn(Optional.of(existing));

    CreateWaterLogRequest request = new CreateWaterLogRequest(300, date, null, clientUuid, null);
    WaterLogResponse response = waterLogService.logWater(userId, request);

    assertThat(response.id()).isEqualTo(existing.getId());
    verify(waterLogRepository, never()).save(any());
    verify(dailySummaryService, never()).recalculate(any(), any());
  }

  @Test
  @DisplayName("deleteWaterLog() deletes log and triggers recalculate")
  void deleteWaterLog_success() {
    UUID logId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 11, 20);
    WaterLog log = new WaterLog();
    log.setId(logId);
    log.setLoggedDate(date);

    when(waterLogRepository.findByIdAndUserId(logId, userId)).thenReturn(Optional.of(log));

    waterLogService.deleteWaterLog(userId, logId);

    verify(waterLogRepository).delete(log);
    verify(dailySummaryService).recalculate(userId, date);
  }

  @Test
  @DisplayName("getDailyWater() returns total and user water target")
  void getDailyWater_success() {
    LocalDate date = LocalDate.of(2026, 11, 20);

    WaterLog l1 = new WaterLog();
    l1.setId(UUID.randomUUID());
    l1.setAmountMl(250);
    l1.setLoggedDate(date);

    WaterLog l2 = new WaterLog();
    l2.setId(UUID.randomUUID());
    l2.setAmountMl(500);
    l2.setLoggedDate(date);

    when(waterLogRepository.findByUserIdAndLoggedDateOrderByLoggedAtAsc(userId, date)).thenReturn(List.of(l1, l2));

    UserHealthProfile profile = new UserHealthProfile();
    profile.setWaterGoalMl(2500);
    when(userHealthProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

    DailyWaterResponse response = waterLogService.getDailyWater(userId, date);

    assertThat(response.totalWaterMl()).isEqualTo(750);
    assertThat(response.targetWaterMl()).isEqualTo(2500);
    assertThat(response.logs()).hasSize(2);
  }
}
