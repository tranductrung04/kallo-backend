package com.healthcare.kallo.service.nutri.adapter;

import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.repository.nutri.UserFoodLogRepository;
import com.healthcare.kallo.repository.nutri.WaterLogRepository;
import com.healthcare.kallo.service.act.UserTimeResolver;
import com.healthcare.kallo.service.sys.UserActivityPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Supplies water and meal activity signals from NUTRI logs to SYS reminder policies. */
@Component
public class DailySummaryUserActivityPortImpl implements UserActivityPort {

  private final WaterLogRepository waterLogRepository;
  private final UserFoodLogRepository userFoodLogRepository;
  private final UserTimeResolver userTimeResolver;
  private final Clock clock;

  public DailySummaryUserActivityPortImpl(
      WaterLogRepository waterLogRepository,
      UserFoodLogRepository userFoodLogRepository,
      UserTimeResolver userTimeResolver,
      Clock clock) {
    this.waterLogRepository = waterLogRepository;
    this.userFoodLogRepository = userFoodLogRepository;
    this.userTimeResolver = userTimeResolver;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Instant> lastWaterLogAt(UUID userId) {
    if (userId == null) {
      return Optional.empty();
    }
    return waterLogRepository.findLastLoggedAtByUserId(userId);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasMealLog(UUID userId, LocalDate date, String mealType) {
    MealType parsed = parseMealType(mealType);
    if (userId == null || date == null || parsed == null) {
      return false;
    }
    return userFoodLogRepository.existsByUserIdAndLoggedDateAndMealTypeAndDeletedAtIsNull(
        userId, date, parsed);
  }

  @Override
  @Transactional(readOnly = true)
  public List<LocalTime> recentMealLogTimes(UUID userId, String mealType, int days) {
    MealType parsed = parseMealType(mealType);
    if (userId == null || parsed == null || days <= 0) {
      return List.of();
    }
    ZoneId zone = userTimeResolver.zoneOf(userId);
    Instant since = Instant.now(clock).minus(days, ChronoUnit.DAYS);
    return userFoodLogRepository.findRecentLoggedAtByMealType(userId, parsed, since).stream()
        .map(instant -> instant.atZone(zone).toLocalTime())
        .toList();
  }

  private static MealType parseMealType(String mealType) {
    if (mealType == null || mealType.isBlank()) {
      return null;
    }
    try {
      return MealType.valueOf(mealType.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
