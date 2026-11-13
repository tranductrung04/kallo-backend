package com.healthcare.kallo.service.sys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.healthcare.kallo.config.NotificationProperties;
import com.healthcare.kallo.config.SyncProperties;
import com.healthcare.kallo.repository.sys.DeviceTokenRepository;
import com.healthcare.kallo.repository.sys.NotificationRepository;
import com.healthcare.kallo.service.sys.sync.SyncEntityHandler;
import com.healthcare.kallo.service.sys.sync.SyncEntityType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

class SysMaintenanceJobTest {
  @Test
  void cleansNotificationsDevicesAndContinuesAfterHandlerFailure() {
    NotificationRepository notifications = mock(NotificationRepository.class);
    DeviceTokenRepository devices = mock(DeviceTokenRepository.class);
    SyncEntityHandler failing = mock(SyncEntityHandler.class);
    SyncEntityHandler healthy = mock(SyncEntityHandler.class);
    when(failing.type()).thenReturn(SyncEntityType.WATER_LOG);
    when(healthy.type()).thenReturn(SyncEntityType.WEIGHT_LOG);
    doThrow(new IllegalStateException("handler down")).when(failing).cleanupTombstonesBefore(any());
    PlatformTransactionManager tx = new NoopTransactionManager();
    var job = new SysMaintenanceJob(
        notifications,
        devices,
        new NotificationProperties(30, 6, 10, 60, Duration.ofMinutes(10)),
        new SyncProperties(200, Duration.ofSeconds(2), Duration.ofDays(30), 500, 1000, Duration.ofMinutes(5)),
        List.of(failing, healthy),
        Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"), ZoneOffset.UTC),
        tx);

    job.run();

    ArgumentCaptor<Instant> notificationCutoff = ArgumentCaptor.forClass(Instant.class);
    ArgumentCaptor<Instant> deviceCutoff = ArgumentCaptor.forClass(Instant.class);
    ArgumentCaptor<Instant> tombstoneCutoff = ArgumentCaptor.forClass(Instant.class);
    verify(notifications).deleteCreatedBefore(notificationCutoff.capture());
    verify(devices).deleteLastSeenBefore(deviceCutoff.capture());
    verify(failing).cleanupTombstonesBefore(tombstoneCutoff.capture());
    verify(healthy).cleanupTombstonesBefore(tombstoneCutoff.capture());
    assertThat(notificationCutoff.getValue()).isEqualTo(Instant.parse("2026-10-14T00:00:00Z"));
    assertThat(deviceCutoff.getValue()).isEqualTo(Instant.parse("2026-09-14T00:00:00Z"));
    assertThat(tombstoneCutoff.getAllValues()).containsOnly(Instant.parse("2026-10-14T00:00:00Z"));

  }

  @Test
  void notificationCleanupFailureDoesNotSkipDevicesOrHandlers() {
    NotificationRepository notifications = mock(NotificationRepository.class);
    DeviceTokenRepository devices = mock(DeviceTokenRepository.class);
    SyncEntityHandler handler = mock(SyncEntityHandler.class);
    when(handler.type()).thenReturn(SyncEntityType.WATER_LOG);
    doThrow(new IllegalStateException("database down")).when(notifications).deleteCreatedBefore(any());
    var job = new SysMaintenanceJob(
        notifications, devices,
        new NotificationProperties(30, 6, 10, 60, Duration.ofMinutes(10)),
        new SyncProperties(200, Duration.ofSeconds(2), Duration.ofDays(30), 500, 1000, Duration.ofMinutes(5)),
        List.of(handler), Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"), ZoneOffset.UTC),
        new NoopTransactionManager());

    job.run();

    verify(devices).deleteLastSeenBefore(Instant.parse("2026-09-14T00:00:00Z"));
    verify(handler).cleanupTombstonesBefore(Instant.parse("2026-10-14T00:00:00Z"));
  }

  private static final class NoopTransactionManager implements PlatformTransactionManager {
    public TransactionStatus getTransaction(TransactionDefinition definition) { return new org.springframework.transaction.support.SimpleTransactionStatus(); }
    public void commit(TransactionStatus status) {}
    public void rollback(TransactionStatus status) {}
  }
}
