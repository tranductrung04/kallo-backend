package com.healthcare.kallo.service.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.healthcare.kallo.event.act.WeightChangedEvent;
import com.healthcare.kallo.model.act.WeightLog;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.model.profile.enums.ActivityLevel;
import com.healthcare.kallo.model.profile.enums.Gender;
import com.healthcare.kallo.model.profile.enums.GoalType;
import com.healthcare.kallo.repository.act.WeightLogRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileWeightEventListenerTest {

  @Mock private UserHealthProfileRepository profileRepository;
  @Mock private WeightLogRepository weightLogRepository;

  private final Clock fixedClock =
      Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneId.of("UTC"));

  private ProfileWeightEventListener listener;

  @BeforeEach
  void setUp() {
    listener = new ProfileWeightEventListener(profileRepository, weightLogRepository, fixedClock);
  }

  @Test
  @DisplayName("onWeightChanged triggers recommendation when TDEE diff >= 100 kcal")
  void onWeightChanged_triggersRecommendation() {
    UUID userId = UUID.randomUUID();
    UserHealthProfile profile = new UserHealthProfile();
    profile.setId(UUID.randomUUID());
    profile.setGender(Gender.MALE);
    profile.setBirthDate(LocalDate.of(1996, 1, 1));
    profile.setHeightCm(BigDecimal.valueOf(175.0));
    profile.setWeightKg(BigDecimal.valueOf(70.0));
    profile.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE); // 1.55 multiplier
    profile.setGoalType(GoalType.MAINTAIN_WEIGHT);
    profile.setTdee(2500);
    profile.setWeeklyRateKg(BigDecimal.ZERO);

    // New weight is 78.0 kg (8kg increase -> BMR increase = 80 kcal -> TDEE increase = 80 * 1.55 = 124 kcal >= 100)
    WeightLog log = new WeightLog();
    log.setWeightKg(BigDecimal.valueOf(78.0));

    when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(weightLogRepository.findLatestAtOrBefore(eq(userId), any(Instant.class)))
        .thenReturn(Optional.of(log));

    listener.onWeightChanged(new WeightChangedEvent(userId, LocalDate.of(2026, 11, 17)));

    ArgumentCaptor<UserHealthProfile> captor = ArgumentCaptor.forClass(UserHealthProfile.class);
    verify(profileRepository).save(captor.capture());

    UserHealthProfile saved = captor.getValue();
    assertThat(saved.isRecalculationRecommended()).isTrue();
    assertThat(saved.getRecommendedTdee()).isNotNull();
    assertThat(saved.getRecommendedDailyCalorieGoal()).isNotNull();
    assertThat(saved.getLatestLoggedWeightKg()).isEqualByComparingTo(BigDecimal.valueOf(78.0));
  }

  @Test
  @DisplayName("onWeightChanged ignores when TDEE diff < 100 kcal")
  void onWeightChanged_ignoresSmallDiff() {
    UUID userId = UUID.randomUUID();
    UserHealthProfile profile = new UserHealthProfile();
    profile.setId(UUID.randomUUID());
    profile.setGender(Gender.MALE);
    profile.setBirthDate(LocalDate.of(1996, 1, 1));
    profile.setHeightCm(BigDecimal.valueOf(175.0));
    profile.setWeightKg(BigDecimal.valueOf(70.0));
    profile.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE);
    profile.setGoalType(GoalType.MAINTAIN_WEIGHT);
    profile.setTdee(2595);
    profile.setWeeklyRateKg(BigDecimal.ZERO);

    // New weight is 70.5 kg (0.5kg difference -> diff ~ 8 kcal << 100)
    WeightLog log = new WeightLog();
    log.setWeightKg(BigDecimal.valueOf(70.5));

    when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(weightLogRepository.findLatestAtOrBefore(eq(userId), any(Instant.class)))
        .thenReturn(Optional.of(log));

    listener.onWeightChanged(new WeightChangedEvent(userId, LocalDate.of(2026, 11, 17)));

    verify(profileRepository, never()).save(any());
  }
}
