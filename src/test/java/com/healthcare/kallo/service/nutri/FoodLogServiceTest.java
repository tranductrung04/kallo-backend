package com.healthcare.kallo.service.nutri;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.request.nutri.CopyMealRequest;
import com.healthcare.kallo.dto.request.nutri.CreateFoodLogRequest;
import com.healthcare.kallo.dto.request.nutri.UpdateFoodLogRequest;
import com.healthcare.kallo.dto.response.nutri.DailyFoodLogResponse;
import com.healthcare.kallo.dto.response.nutri.FoodLogResponse;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.model.nutri.UserFoodLog;
import com.healthcare.kallo.model.nutri.enums.FoodLogSource;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.food.FoodRepository;
import com.healthcare.kallo.repository.nutri.UserFoodLogRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.nutri.impl.FoodLogServiceImpl;
import com.healthcare.kallo.service.report.DailySummaryService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class FoodLogServiceTest {

  @Mock private UserFoodLogRepository userFoodLogRepository;
  @Mock private FoodRepository foodRepository;
  @Mock private UserRepository userRepository;
  @Mock private UserHealthProfileRepository userHealthProfileRepository;
  @Mock private DailySummaryService dailySummaryService;
  @Mock private ApplicationEventPublisher eventPublisher;

  private final Clock clock = Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneId.of("UTC"));
  private FoodLogService foodLogService;

  private UUID userId;
  private UUID foodId;
  private User user;
  private Food food;

  @BeforeEach
  void setUp() {
    foodLogService = new FoodLogServiceImpl(
        userFoodLogRepository,
        foodRepository,
        userRepository,
        userHealthProfileRepository,
        dailySummaryService,
        eventPublisher,
        clock
    );

    userId = UUID.randomUUID();
    foodId = UUID.randomUUID();

    user = new User();
    user.setId(userId);
    user.setEmail("user@example.com");

    food = new Food();
    food.setId(foodId);
    food.setName("Phở bò");
    food.setCategory(FoodCategory.NOODLES);
    food.setDefaultServingName("1 tô vừa");
    food.setDefaultServingGrams(new BigDecimal("400.0"));
    food.setCaloriesPer100g(new BigDecimal("112.5"));
    food.setProteinPer100g(new BigDecimal("7.5"));
    food.setCarbsPer100g(new BigDecimal("15.0"));
    food.setFatPer100g(new BigDecimal("2.5"));
    food.setSugarPer100g(new BigDecimal("1.0"));
    food.setFiberPer100g(new BigDecimal("0.5"));
    food.setAllergens(List.of("GLUTEN"));
  }

  @Test
  @DisplayName("logFood() computes snapshot nutrients correctly and recalculates summary")
  void logFood_success() {
    UUID clientUuid = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 11, 20);
    CreateFoodLogRequest request = new CreateFoodLogRequest(
        foodId,
        MealType.BREAKFAST,
        "1 tô vừa",
        new BigDecimal("400.0"),
        new BigDecimal("1.0"),
        FoodLogSource.SEARCH,
        date,
        null,
        clientUuid,
        null,
        null
    );

    when(userFoodLogRepository.findByUserIdAndClientUuid(userId, clientUuid)).thenReturn(Optional.empty());
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(foodRepository.findById(foodId)).thenReturn(Optional.of(food));
    when(userHealthProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
    when(userFoodLogRepository.save(any(UserFoodLog.class))).thenAnswer(invocation -> {
      UserFoodLog log = invocation.getArgument(0);
      log.setId(UUID.randomUUID());
      return log;
    });

    FoodLogResponse response = foodLogService.logFood(userId, request);

    assertThat(response).isNotNull();
    assertThat(response.foodName()).isEqualTo("Phở bò");
    assertThat(response.mealType()).isEqualTo(MealType.BREAKFAST);
    // 400g / 100g * 112.5 = 450.0 kcal
    assertThat(response.calories()).isEqualByComparingTo(new BigDecimal("450.0"));
    // 400g / 100g * 7.5 = 30.0 g protein
    assertThat(response.proteinG()).isEqualByComparingTo(new BigDecimal("30.0"));
    assertThat(response.carbsG()).isEqualByComparingTo(new BigDecimal("60.0"));
    assertThat(response.fatG()).isEqualByComparingTo(new BigDecimal("10.0"));

    verify(userFoodLogRepository).save(any(UserFoodLog.class));
    verify(dailySummaryService).recalculate(userId, date);
  }

  @Test
  @DisplayName("logFood() detects allergy warning when user profile matches food allergens")
  void logFood_allergyWarning() {
    UUID clientUuid = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 11, 20);
    CreateFoodLogRequest request = new CreateFoodLogRequest(
        foodId,
        MealType.BREAKFAST,
        "1 tô vừa",
        new BigDecimal("400.0"),
        BigDecimal.ONE,
        FoodLogSource.SEARCH,
        date,
        null,
        clientUuid,
        null,
        null
    );

    UserHealthProfile profile = new UserHealthProfile();
    profile.setAllergies(List.of("GLUTEN", "PEANUT"));

    when(userFoodLogRepository.findByUserIdAndClientUuid(userId, clientUuid)).thenReturn(Optional.empty());
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(foodRepository.findById(foodId)).thenReturn(Optional.of(food));
    when(userHealthProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userFoodLogRepository.save(any(UserFoodLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

    FoodLogResponse response = foodLogService.logFood(userId, request);

    assertThat(response.hasAllergyWarning()).isTrue();
    assertThat(response.allergyWarnings()).contains("GLUTEN");
  }

  @Test
  @DisplayName("logFood() returns existing record idempotently when clientUuid matches")
  void logFood_idempotent() {
    UUID clientUuid = UUID.randomUUID();
    UserFoodLog existing = new UserFoodLog();
    existing.setId(UUID.randomUUID());
    existing.setUser(user);
    existing.setFood(food);
    existing.setMealType(MealType.LUNCH);
    existing.setServingName("1 tô");
    existing.setServingGrams(new BigDecimal("400.0"));
    existing.setServingQuantity(BigDecimal.ONE);
    existing.setCalories(new BigDecimal("450.0"));
    existing.setProteinG(new BigDecimal("30.0"));
    existing.setCarbsG(new BigDecimal("60.0"));
    existing.setFatG(new BigDecimal("10.0"));
    existing.setSugarG(BigDecimal.ZERO);
    existing.setFiberG(BigDecimal.ZERO);
    existing.setClientUuid(clientUuid);
    existing.setLoggedDate(LocalDate.of(2026, 11, 20));

    when(userFoodLogRepository.findByUserIdAndClientUuid(userId, clientUuid)).thenReturn(Optional.of(existing));

    CreateFoodLogRequest request = new CreateFoodLogRequest(
        foodId, MealType.LUNCH, null, null, null, null, LocalDate.of(2026, 11, 20), null, clientUuid, null, null
    );

    FoodLogResponse response = foodLogService.logFood(userId, request);

    assertThat(response.id()).isEqualTo(existing.getId());
    verify(userFoodLogRepository, never()).save(any());
    verify(dailySummaryService, never()).recalculate(any(), any());
  }

  @Test
  @DisplayName("deleteFoodLog() removes log and triggers recalculate")
  void deleteFoodLog_success() {
    UUID logId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 11, 20);
    UserFoodLog log = new UserFoodLog();
    log.setId(logId);
    log.setUser(user);
    log.setLoggedDate(date);

    when(userFoodLogRepository.findByIdAndUserId(logId, userId)).thenReturn(Optional.of(log));

    foodLogService.deleteFoodLog(userId, logId);

    verify(userFoodLogRepository).delete(log);
    verify(dailySummaryService).recalculate(userId, date);
  }

  @Test
  @DisplayName("getDailyFoodLogs() aggregates meals by type and sums nutrients")
  void getDailyFoodLogs_success() {
    LocalDate date = LocalDate.of(2026, 11, 20);

    UserFoodLog breakfastLog = new UserFoodLog();
    breakfastLog.setId(UUID.randomUUID());
    breakfastLog.setFood(food);
    breakfastLog.setMealType(MealType.BREAKFAST);
    breakfastLog.setServingName("1 tô");
    breakfastLog.setServingGrams(new BigDecimal("400.0"));
    breakfastLog.setServingQuantity(BigDecimal.ONE);
    breakfastLog.setCalories(new BigDecimal("450.0"));
    breakfastLog.setProteinG(new BigDecimal("30.0"));
    breakfastLog.setCarbsG(new BigDecimal("60.0"));
    breakfastLog.setFatG(new BigDecimal("10.0"));
    breakfastLog.setSugarG(new BigDecimal("4.0"));
    breakfastLog.setFiberG(new BigDecimal("2.0"));
    breakfastLog.setLoggedDate(date);

    when(userFoodLogRepository.findByUserIdAndLoggedDateOrderByLoggedAtAsc(userId, date))
        .thenReturn(List.of(breakfastLog));

    UserHealthProfile profile = new UserHealthProfile();
    profile.setDailyCalorieGoal(2200);
    when(userHealthProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

    DailyFoodLogResponse response = foodLogService.getDailyFoodLogs(userId, date);

    assertThat(response.date()).isEqualTo(date);
    assertThat(response.targetCalories()).isEqualTo(2200);
    assertThat(response.totalCalories()).isEqualByComparingTo(new BigDecimal("450.0"));
    assertThat(response.meals().get(MealType.BREAKFAST)).hasSize(1);
    assertThat(response.meals().get(MealType.LUNCH)).isEmpty();
  }
}
