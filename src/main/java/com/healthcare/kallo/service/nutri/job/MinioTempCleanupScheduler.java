package com.healthcare.kallo.service.nutri.job;

import com.healthcare.kallo.config.NutriProperties;
import com.healthcare.kallo.service.nutri.NutriImageStorageService;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Nightly maintenance job cleaning expired temporary meal recognition photos in MinIO. */
@Component
public class MinioTempCleanupScheduler {

  private static final Logger log = LoggerFactory.getLogger(MinioTempCleanupScheduler.class);

  private final NutriImageStorageService nutriImageStorageService;
  private final NutriProperties nutriProperties;
  private final Clock clock;

  public MinioTempCleanupScheduler(
      NutriImageStorageService nutriImageStorageService,
      NutriProperties nutriProperties,
      Clock clock) {
    this.nutriImageStorageService = nutriImageStorageService;
    this.nutriProperties = nutriProperties;
    this.clock = clock;
  }

  @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Ho_Chi_Minh")
  public void cleanupExpiredTempImages() {
    Map<String, String> previousMdc = MDC.getCopyOfContextMap();
    MDC.put("request_id", "async-" + UUID.randomUUID());
    try {
      Instant cutoff = Instant.now(clock).minus(nutriProperties.getStorage().getTempRetention());
      int deleted = nutriImageStorageService.cleanupExpiredTempObjects(cutoff);
      log.info("Cleaned up {} expired temporary meal photos", deleted);
    } catch (RuntimeException e) {
      log.error("Failed to clean up expired temporary meal photos", e);
    } finally {
      if (previousMdc == null) {
        MDC.clear();
      } else {
        MDC.setContextMap(previousMdc);
      }
    }
  }
}
