package com.healthcare.kallo.service.act;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.model.act.WeightLog;
import com.healthcare.kallo.repository.act.WeightLogRepository;
import com.healthcare.kallo.service.act.impl.BodyMetricsServiceImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class BodyMetricsServiceTest {
  @Mock WeightLogRepository weightLogs;
  @Mock ProfileBodyMetrics profile;
  @Mock ObjectProvider<ProfileBodyMetrics> profiles;

  private BodyMetricsService service;
  private UUID userId;
  private Instant instant;

  @BeforeEach
  void setUp() {
    service = new BodyMetricsServiceImpl(weightLogs, profiles);
    userId = UUID.randomUUID();
    instant = Instant.parse("2026-11-11T00:00:00Z");
  }

  @Test
  void prefersLatestWeightLog() {
    WeightLog log = new WeightLog();
    log.setWeightKg(BigDecimal.valueOf(72.5));
    when(weightLogs.findTopByUserIdAndDeletedAtIsNullAndLoggedAtLessThanEqualOrderByLoggedAtDesc(userId, instant))
        .thenReturn(Optional.of(log));

    assertThat(service.weightAt(userId, instant)).contains(BigDecimal.valueOf(72.5));
  }

  @Test
  void fallsBackToProfileWhenNoWeightLogExists() {
    when(weightLogs.findTopByUserIdAndDeletedAtIsNullAndLoggedAtLessThanEqualOrderByLoggedAtDesc(userId, instant))
        .thenReturn(Optional.empty());
    when(profiles.getIfAvailable()).thenReturn(profile);
    when(profile.weightKg(userId)).thenReturn(Optional.of(BigDecimal.valueOf(68)));

    assertThat(service.weightAt(userId, instant)).contains(BigDecimal.valueOf(68));
  }

  @Test
  void returnsEmptyWhenNeitherSourceHasWeight() {
    when(weightLogs.findTopByUserIdAndDeletedAtIsNullAndLoggedAtLessThanEqualOrderByLoggedAtDesc(userId, instant))
        .thenReturn(Optional.empty());
    when(profiles.getIfAvailable()).thenReturn(null);

    assertThat(service.weightAt(userId, instant)).isEmpty();
  }
}
