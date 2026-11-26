package com.healthcare.kallo.service.nutri.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.GamePropertiesFixture;
import com.healthcare.kallo.model.nutri.DailySummary;
import com.healthcare.kallo.repository.nutri.DailySummaryRepository;
import com.healthcare.kallo.service.game.DailyProgress;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailySummaryProgressProviderImplTest {

  @Mock private DailySummaryRepository dailySummaryRepository;

  private DailySummaryProgressProviderImpl provider;
  private UUID userId;

  @BeforeEach
  void setUp() {
    provider =
        new DailySummaryProgressProviderImpl(
            dailySummaryRepository, GamePropertiesFixture.defaults());
    userId = UUID.randomUUID();
  }

  @Test
  void mapsDailySummaryAndOmitsMissingDates() {
    LocalDate date = LocalDate.of(2026, 11, 25);
    DailySummary summary = new DailySummary();
    summary.setUserId(userId);
    summary.setSummaryDate(date);
    summary.setMealsLoggedCount(2);
    summary.setCaloriesConsumed(1850);
    summary.setTargetCalories(2000);
    summary.setWaterMl(1500);
    summary.setTargetWaterMl(2000);
    summary.setSteps(6500);
    summary.setCaloriesBurned(320);

    when(dailySummaryRepository.findByUserIdAndSummaryDate(userId, date))
        .thenReturn(Optional.of(summary));
    when(dailySummaryRepository.findByUserIdAndSummaryDateBetweenOrderBySummaryDateAsc(
            userId, date.minusDays(2), date))
        .thenReturn(List.of(summary));
    when(dailySummaryRepository.sumStepsInRange(userId, date.minusDays(2), date)).thenReturn(6500L);

    Optional<DailyProgress> single = provider.find(userId, date);
    assertThat(single).isPresent();
    assertThat(single.get().mealsLogged()).isEqualTo(2);
    assertThat(single.get().caloriesIn()).isEqualTo(1850);
    assertThat(single.get().waterGoalMl()).isEqualTo(2000);

    List<DailyProgress> range = provider.findRange(userId, date.minusDays(2), date);
    assertThat(range).hasSize(1);
    assertThat(provider.sumSteps(userId, date.minusDays(2), date)).isEqualTo(6500L);
  }

  @Test
  void rejectsRangesExceedingConfiguredWindowDays() {
    LocalDate end = LocalDate.of(2026, 11, 25);
    LocalDate tooFar = end.minusDays(400);

    assertThatThrownBy(() -> provider.findRange(userId, tooFar, end))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_TIME_RANGE);

    assertThatThrownBy(() -> provider.sumSteps(userId, tooFar, end))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_TIME_RANGE);
  }
}
