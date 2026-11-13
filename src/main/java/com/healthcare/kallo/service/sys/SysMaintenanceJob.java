package com.healthcare.kallo.service.sys;

import com.healthcare.kallo.config.NotificationProperties;
import com.healthcare.kallo.config.SyncProperties;
import com.healthcare.kallo.repository.sys.DeviceTokenRepository;
import com.healthcare.kallo.repository.sys.NotificationRepository;
import com.healthcare.kallo.service.sys.sync.SyncEntityHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class SysMaintenanceJob {
  private static final Logger log = LoggerFactory.getLogger(SysMaintenanceJob.class);
  private final NotificationRepository notifications;
  private final DeviceTokenRepository devices;
  private final NotificationProperties properties;
  private final SyncProperties syncProperties;
  private final List<SyncEntityHandler> syncHandlers;
  private final Clock clock;
  private final TransactionTemplate transactions;

  public SysMaintenanceJob(
      NotificationRepository notifications,
      DeviceTokenRepository devices,
      NotificationProperties properties,
      SyncProperties syncProperties,
      List<SyncEntityHandler> syncHandlers,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.notifications = notifications;
    this.devices = devices;
    this.properties = properties;
    this.syncProperties = syncProperties;
    this.syncHandlers = syncHandlers;
    this.clock = clock;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Ho_Chi_Minh")
  public void run() {
    Map<String, String> previous = MDC.getCopyOfContextMap();
    MDC.put("request_id", "async-" + UUID.randomUUID());
    try {
      runCleanup();
    } finally {
      if (previous == null) MDC.clear();
      else MDC.setContextMap(previous);
    }
  }

  private void runCleanup() {
    Instant now = Instant.now(clock);
    try {
      int deleted = transactions.execute(status ->
          notifications.deleteCreatedBefore(now.minus(properties.retentionDays(), ChronoUnit.DAYS)));
      log.info("Deleted {} old notifications", deleted);
    } catch (RuntimeException e) {
      log.error("Failed to clean old notifications", e);
    }
    try {
      int deleted = transactions.execute(status ->
          devices.deleteLastSeenBefore(now.minus(properties.deviceRetentionDays(), ChronoUnit.DAYS)));
      log.info("Deleted {} stale devices", deleted);
    } catch (RuntimeException e) {
      log.error("Failed to clean stale devices", e);
    }
    Instant tombstoneCutoff = now.minus(syncProperties.tombstoneRetention());
    syncHandlers.forEach(handler -> {
      try {
        int deleted = transactions.execute(status -> handler.cleanupTombstonesBefore(tombstoneCutoff));
        log.info("Deleted {} tombstones for {}", deleted, handler.type());
      } catch (RuntimeException e) {
        log.error("Failed to clean tombstones for {}", handler.type(), e);
      }
    });
  }
}
