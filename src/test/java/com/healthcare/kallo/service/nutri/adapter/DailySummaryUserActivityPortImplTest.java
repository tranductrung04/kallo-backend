package com.healthcare.kallo.service.nutri.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.repository.nutri.UserFoodLogRepository;
import com.healthcare.kallo.repository.nutri.WaterLogRepository;
import com.healthcare.kallo.service.act.UserTimeResolver;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailySummaryUserActivityPortImplTest {

  private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final Instant FIXED_NOW = Instant.parse("2026-11-25T05:00:00Z");

  @Mock private WaterLogRepository waterLogRepository;
  @Mock private UserFoodLogRepository userFoodLogRepository;
  @Mock private UserTimeResolver userTimeResolver;

  private DailySummaryUserActivityPortImpl port;
  private UUID userId;

  @BeforeEach
  void setUp() {
    port =
        new DailySummaryUserActivityPortImpl(
            waterLogRepository,
            userFoodLogRepository,
            userTimeResolver,
            Clock.fixed(FIXED_NOW, VN_ZONE));
    userId = UUID.randomUUID();
  }

  @Test
  void delegatesWaterAndMealQueriesAndConvertsRecentTimesToUserZone() {
    LocalDate date = LocalDate.of(2026, 11, 25);
    Instant lastWater = Instant.parse("2026-11-25T04:00:00Z");
    when(waterLogRepository.findLastLoggedAtByUserId(userId)).thenReturn(Optional.of(lastWater));
    when(userFoodLogRepository.existsByUserIdAndLoggedDateAndMealTypeAndDeletedAtIsNull(
            userId, date, MealType.BREAKFAST))
        .thenReturn(true);
    when(userTimeResolver.zoneOf(userId)).thenReturn(VN_ZONE);
    when(userFoodLogRepository.findRecentLoggedAtByMealType(
            userId, MealType.BREAKFAST, Instant.parse("2026-11-11T05:00:00Z")))
        .thenReturn(List.of(Instant.parse("2026-11-24T01:15:00Z")));

    assertThat(port.lastWaterLogAt(userId)).contains(lastWater);
    assertThat(port.hasMealLog(userId, date, "BREAKFAST")).isTrue();
    assertThat(port.hasMealLog(userId, date, "UNKNOWN_SLOT")).isFalse();
    assertThat(port.recentMealLogTimes(userId, "BREAKFAST", 14))
        .containsExactly(LocalTime.of(8, 15));
  }
}
