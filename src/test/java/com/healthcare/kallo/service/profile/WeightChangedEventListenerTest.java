package com.healthcare.kallo.service.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.healthcare.kallo.event.act.WeightChangedEvent;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.model.profile.enums.ActivityLevel;
import com.healthcare.kallo.model.profile.enums.Gender;
import com.healthcare.kallo.model.profile.enums.GoalType;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class WeightChangedEventListenerTest {

  @Mock private UserHealthProfileRepository profileRepository;

  private ProfileDomainService domainService;
  private Clock clock;
  private ProfileWeightChangeProcessor processor;
  private WeightChangedEventListener listener;

  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    domainService = new ProfileDomainService();
    clock = Clock.fixed(Instant.parse("2026-11-24T10:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    processor = new ProfileWeightChangeProcessor(profileRepository, domainService, clock);
    listener = new WeightChangedEventListener(processor);
  }

  @Test
  @DisplayName("Khi độ lệch TDEE >= 50 kcal: bật cờ recalculation_recommended = true và tính recommended_tdee")
  void onWeightChanged_largeDifference_recommendsRecalculation() {
    UserHealthProfile profile = new UserHealthProfile();
    profile.setUserId(userId);
    profile.setGender(Gender.MALE);
    profile.setDob(LocalDate.of(1990, 1, 1));
    profile.setHeightCm(new BigDecimal("175.00"));
    profile.setWeightKg(new BigDecimal("70.00"));
    profile.setActivityLevel(ActivityLevel.SEDENTARY);
    profile.setGoalType(GoalType.MAINTAIN_WEIGHT);
    profile.setBmr(new BigDecimal("1588.75"));
    profile.setTdee(new BigDecimal("1906.50"));
    profile.setDailyCalorieGoal(new BigDecimal("1906.50"));
    profile.setRecalculationRecommended(false);

    when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));

    // Change weight from 70kg to 75kg (+5kg with PAL 1.2 -> BMR +50, TDEE +60 kcal >= 50 kcal)
    WeightChangedEvent event = new WeightChangedEvent(userId, null, new BigDecimal("75.00"), Instant.now());
    listener.onWeightChanged(event);

    assertThat(profile.isRecalculationRecommended()).isTrue();
    assertThat(profile.getRecommendedTdee()).isNotNull();
    assertThat(profile.getRecommendedDailyCalorieGoal()).isNotNull();
    assertThat(profile.getLatestLoggedWeightKg()).isEqualByComparingTo("75.00");
    verify(profileRepository).saveAndFlush(profile);
  }

  @Test
  @DisplayName("Khi độ lệch TDEE < 50 kcal: nếu cờ đang bật thì tự tắt recalculation_recommended = false")
  void onWeightChanged_smallDifference_clearsRecalculation() {
    UserHealthProfile profile = new UserHealthProfile();
    profile.setUserId(userId);
    profile.setGender(Gender.MALE);
    profile.setDob(LocalDate.of(1990, 1, 1));
    profile.setHeightCm(new BigDecimal("175.00"));
    profile.setWeightKg(new BigDecimal("70.00"));
    profile.setActivityLevel(ActivityLevel.SEDENTARY);
    profile.setGoalType(GoalType.MAINTAIN_WEIGHT);
    profile.setBmr(new BigDecimal("1588.75"));
    profile.setTdee(new BigDecimal("1906.50"));
    profile.setDailyCalorieGoal(new BigDecimal("1906.50"));
    profile.setRecalculationRecommended(true);
    profile.setRecommendedTdee(new BigDecimal("2000.00"));

    when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));

    // Change weight from 70kg to 70.5kg (very small change, < 50 kcal difference)
    WeightChangedEvent event = new WeightChangedEvent(userId, null, new BigDecimal("70.50"), Instant.now());
    listener.onWeightChanged(event);

    assertThat(profile.isRecalculationRecommended()).isFalse();
    assertThat(profile.getRecommendedTdee()).isNull();
    assertThat(profile.getRecommendedDailyCalorieGoal()).isNull();
    assertThat(profile.getLatestLoggedWeightKg()).isEqualByComparingTo("70.50");
    verify(profileRepository).saveAndFlush(profile);
  }

  @Test
  @DisplayName("Khi xóa toàn bộ log cân nặng (weightKg == null): tắt recalculation và xóa recommended fields")
  void onWeightChanged_deletedAllWeights_clearsRecommendations() {
    UserHealthProfile profile = new UserHealthProfile();
    profile.setUserId(userId);
    profile.setRecalculationRecommended(true);
    profile.setRecommendedTdee(new BigDecimal("2000.00"));
    profile.setLatestLoggedWeightKg(new BigDecimal("75.00"));

    when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));

    WeightChangedEvent event = new WeightChangedEvent(userId, null, null, Instant.now());
    listener.onWeightChanged(event);

    assertThat(profile.isRecalculationRecommended()).isFalse();
    assertThat(profile.getRecommendedTdee()).isNull();
    assertThat(profile.getRecommendedDailyCalorieGoal()).isNull();
    assertThat(profile.getLatestLoggedWeightKg()).isNull();
    verify(profileRepository).saveAndFlush(profile);
  }

  @Test
  @DisplayName("Retry cơ chế Exponential Backoff tối đa 3 lần khi dính OptimisticLockingFailureException")
  void onWeightChanged_retriesOnOptimisticLockingFailure() {
    ProfileWeightChangeProcessor mockProcessor = mock(ProfileWeightChangeProcessor.class);
    doThrow(new OptimisticLockingFailureException("Version conflict"))
        .doThrow(new OptimisticLockingFailureException("Version conflict"))
        .doNothing()
        .when(mockProcessor).process(eq(userId), any());

    WeightChangedEventListener retryListener = new WeightChangedEventListener(mockProcessor);
    WeightChangedEvent event = new WeightChangedEvent(userId, null, new BigDecimal("72.00"), Instant.now());

    retryListener.onWeightChanged(event);

    verify(mockProcessor, times(3)).process(eq(userId), eq(new BigDecimal("72.00")));
  }
}
