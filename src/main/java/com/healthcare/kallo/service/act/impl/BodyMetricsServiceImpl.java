package com.healthcare.kallo.service.act.impl;

import com.healthcare.kallo.repository.act.WeightLogRepository;
import com.healthcare.kallo.service.act.BodyMetricsService;
import com.healthcare.kallo.service.act.ProfileBodyMetrics;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class BodyMetricsServiceImpl implements BodyMetricsService {
  private final WeightLogRepository weightLogs;
  private final ObjectProvider<ProfileBodyMetrics> profileBodyMetricsProvider;

  public BodyMetricsServiceImpl(
      WeightLogRepository weightLogs,
      ObjectProvider<ProfileBodyMetrics> profileBodyMetricsProvider) {
    this.weightLogs = weightLogs;
    this.profileBodyMetricsProvider = profileBodyMetricsProvider;
  }

  @Override
  public Optional<BigDecimal> weightAt(UUID userId, Instant instant) {
    Optional<BigDecimal> latestWeight =
        weightLogs
            .findTopByUserIdAndDeletedAtIsNullAndLoggedAtLessThanEqualOrderByLoggedAtDesc(
                userId, instant)
            .map(log -> log.getWeightKg());
    if (latestWeight.isPresent()) {
      return latestWeight;
    }

    ProfileBodyMetrics profileBodyMetrics = profileBodyMetricsProvider.getIfAvailable();
    if (profileBodyMetrics == null) {
      return Optional.empty();
    }
    return profileBodyMetrics.weightKg(userId);
  }
}
