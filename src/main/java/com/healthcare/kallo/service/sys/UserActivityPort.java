package com.healthcare.kallo.service.sys;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserActivityPort {
  Optional<Instant> lastWaterLogAt(UUID userId);

  boolean hasMealLog(UUID userId, LocalDate date, String mealType);

  List<LocalTime> recentMealLogTimes(UUID userId, String mealType, int days);
}
