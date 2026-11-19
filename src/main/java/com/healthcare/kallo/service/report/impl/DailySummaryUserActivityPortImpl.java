package com.healthcare.kallo.service.report.impl;

import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.model.nutri.UserFoodLog;
import com.healthcare.kallo.model.nutri.WaterLog;
import com.healthcare.kallo.repository.nutri.UserFoodLogRepository;
import com.healthcare.kallo.repository.nutri.WaterLogRepository;
import com.healthcare.kallo.service.act.UserTimeResolver;
import com.healthcare.kallo.service.sys.UserActivityPort;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Primary
public class DailySummaryUserActivityPortImpl implements UserActivityPort {

  private final WaterLogRepository waterLogRepository;
  private final UserFoodLogRepository userFoodLogRepository;
  private final UserTimeResolver timeResolver;

  public DailySummaryUserActivityPortImpl(
      WaterLogRepository waterLogRepository,
      UserFoodLogRepository userFoodLogRepository,
      UserTimeResolver timeResolver) {
    this.waterLogRepository = waterLogRepository;
    this.userFoodLogRepository = userFoodLogRepository;
    this.timeResolver = timeResolver;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Instant> lastWaterLogAt(UUID userId) {
    return waterLogRepository.findTopByUserIdOrderByLoggedAtDesc(userId)
        .map(WaterLog::getLoggedAt);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasMealLog(UUID userId, LocalDate date, String mealType) {
    try {
      MealType type = MealType.valueOf(mealType.toUpperCase());
      return userFoodLogRepository.existsByUserIdAndLoggedDateAndMealType(userId, date, type);
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<LocalTime> recentMealLogTimes(UUID userId, String mealType, int days) {
    try {
      MealType type = MealType.valueOf(mealType.toUpperCase());
      ZoneId zone = timeResolver.zoneOf(userId);
      LocalDate today = LocalDate.now(zone);
      LocalDate since = today.minusDays(days);

      return userFoodLogRepository.findByUserIdAndLoggedDateOrderByLoggedAtAsc(userId, today)
          .stream()
          .filter(log -> log.getMealType() == type && !log.getLoggedDate().isBefore(since))
          .map(log -> log.getLoggedAt().atZone(zone).toLocalTime())
          .toList();
    } catch (Exception e) {
      return Collections.emptyList();
    }
  }
}
