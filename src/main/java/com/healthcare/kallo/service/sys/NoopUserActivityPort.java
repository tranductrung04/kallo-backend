package com.healthcare.kallo.service.sys;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public class NoopUserActivityPort implements UserActivityPort {
  @Override
  public Optional<Instant> lastWaterLogAt(UUID userId) {
    return Optional.empty();
  }

  @Override
  public boolean hasMealLog(UUID userId, LocalDate date, String mealType) {
    return false;
  }

  @Override
  public List<LocalTime> recentMealLogTimes(UUID userId, String mealType, int days) {
    return List.of();
  }
}
