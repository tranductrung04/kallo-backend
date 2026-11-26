package com.healthcare.kallo.service.nutri.adapter;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.GameProperties;
import com.healthcare.kallo.model.nutri.DailySummary;
import com.healthcare.kallo.repository.nutri.DailySummaryRepository;
import com.healthcare.kallo.service.game.DailyProgress;
import com.healthcare.kallo.service.game.DailyProgressProvider;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exposes NUTRI daily summaries to module GAME without exposing NUTRI JPA entities.
 */
@Component
public class DailySummaryProgressProviderImpl implements DailyProgressProvider {

  private final DailySummaryRepository dailySummaryRepository;
  private final GameProperties gameProperties;

  public DailySummaryProgressProviderImpl(
      DailySummaryRepository dailySummaryRepository, GameProperties gameProperties) {
    this.dailySummaryRepository = dailySummaryRepository;
    this.gameProperties = gameProperties;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<DailyProgress> find(UUID userId, LocalDate localDate) {
    if (userId == null || localDate == null) {
      throw new BusinessException(ErrorCode.INVALID_PARAMETER);
    }
    return dailySummaryRepository
        .findByUserIdAndSummaryDate(userId, localDate)
        .map(this::toDailyProgress);
  }

  @Override
  @Transactional(readOnly = true)
  public List<DailyProgress> findRange(
      UUID userId, LocalDate fromInclusive, LocalDate toInclusive) {
    validateRange(userId, fromInclusive, toInclusive);
    return dailySummaryRepository
        .findByUserIdAndSummaryDateBetweenOrderBySummaryDateAsc(
            userId, fromInclusive, toInclusive)
        .stream()
        .map(this::toDailyProgress)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long sumSteps(UUID userId, LocalDate fromInclusive, LocalDate toInclusive) {
    validateRange(userId, fromInclusive, toInclusive);
    return dailySummaryRepository.sumStepsInRange(userId, fromInclusive, toInclusive);
  }

  private void validateRange(UUID userId, LocalDate fromInclusive, LocalDate toInclusive) {
    if (userId == null
        || fromInclusive == null
        || toInclusive == null
        || fromInclusive.isAfter(toInclusive)) {
      throw new BusinessException(ErrorCode.INVALID_TIME_RANGE);
    }
    long days = ChronoUnit.DAYS.between(fromInclusive, toInclusive) + 1;
    if (days > gameProperties.streak().windowDays()) {
      throw new BusinessException(ErrorCode.INVALID_TIME_RANGE);
    }
  }

  private DailyProgress toDailyProgress(DailySummary summary) {
    return new DailyProgress(
        summary.getSummaryDate(),
        summary.getMealsLoggedCount(),
        summary.getCaloriesConsumed(),
        summary.getTargetCalories(),
        summary.getWaterMl() != null ? summary.getWaterMl() : 0,
        summary.getTargetWaterMl(),
        summary.getSteps() != null ? summary.getSteps() : 0,
        summary.getCaloriesBurned());
  }
}
