package com.healthcare.kallo.service.report.impl;

import com.healthcare.kallo.model.report.DailySummary;
import com.healthcare.kallo.repository.report.DailySummaryRepository;
import com.healthcare.kallo.service.game.DailyProgress;
import com.healthcare.kallo.service.game.DailyProgressProvider;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Primary
public class DailySummaryProgressProviderImpl implements DailyProgressProvider {

  private final DailySummaryRepository dailySummaryRepository;

  public DailySummaryProgressProviderImpl(DailySummaryRepository dailySummaryRepository) {
    this.dailySummaryRepository = dailySummaryRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<DailyProgress> find(UUID userId, LocalDate localDate) {
    return dailySummaryRepository.findByUserIdAndSummaryDate(userId, localDate)
        .map(this::toProgress);
  }

  @Override
  @Transactional(readOnly = true)
  public List<DailyProgress> findRange(UUID userId, LocalDate fromInclusive, LocalDate toInclusive) {
    return dailySummaryRepository.findByUserIdAndSummaryDateBetweenOrderBySummaryDateAsc(userId, fromInclusive, toInclusive)
        .stream()
        .map(this::toProgress)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long sumSteps(UUID userId, LocalDate fromInclusive, LocalDate toInclusive) {
    return dailySummaryRepository.sumStepsBetween(userId, fromInclusive, toInclusive);
  }

  private DailyProgress toProgress(DailySummary s) {
    return new DailyProgress(
        s.getSummaryDate(),
        s.getMealsLoggedCount(),
        s.getCaloriesConsumed(),
        s.getTargetCalories(),
        s.getWaterMl(),
        s.getTargetWaterMl(),
        s.getSteps(),
        s.getCaloriesBurned()
    );
  }
}
