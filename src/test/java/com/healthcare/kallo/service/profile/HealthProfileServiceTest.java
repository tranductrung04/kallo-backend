package com.healthcare.kallo.service.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.request.profile.HealthProfileRequest;
import com.healthcare.kallo.dto.response.profile.HealthProfileResponse;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.model.profile.enums.ActivityLevel;
import com.healthcare.kallo.model.profile.enums.Gender;
import com.healthcare.kallo.model.profile.enums.GoalType;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.profile.impl.HealthProfileServiceImpl;
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

@ExtendWith(MockitoExtension.class)
class HealthProfileServiceTest {

  @Mock private UserHealthProfileRepository profileRepository;
  @Mock private UserRepository userRepository;

  private final Clock fixedClock =
      Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneId.of("UTC"));

  private HealthProfileService profileService;

  @BeforeEach
  void setUp() {
    profileService =
        new HealthProfileServiceImpl(profileRepository, userRepository, fixedClock);
  }

  @Test
  @DisplayName("getProfile() returns profile when exists")
  void getProfile_exists() {
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setId(userId);

    UserHealthProfile profile = new UserHealthProfile();
    profile.setId(UUID.randomUUID());
    profile.setUser(user);
    profile.setGender(Gender.MALE);
    profile.setBirthDate(LocalDate.of(1995, 1, 1));
    profile.setHeightCm(BigDecimal.valueOf(175.0));
    profile.setWeightKg(BigDecimal.valueOf(70.0));
    profile.setActivityLevel(ActivityLevel.SEDENTARY);
    profile.setGoalType(GoalType.MAINTAIN_WEIGHT);
    profile.setBmi(BigDecimal.valueOf(22.9));
    profile.setBmiCategory(com.healthcare.kallo.model.profile.enums.BmiCategory.NORMAL);
    profile.setBmr(1670);
    profile.setTdee(2004);
    profile.setDailyCalorieGoal(2004);
    profile.setProteinGoalG(BigDecimal.valueOf(100.2));
    profile.setCarbsGoalG(BigDecimal.valueOf(250.5));
    profile.setFatGoalG(BigDecimal.valueOf(66.8));
    profile.setProteinPct(BigDecimal.valueOf(20.0));
    profile.setCarbsPct(BigDecimal.valueOf(50.0));
    profile.setFatPct(BigDecimal.valueOf(30.0));
    profile.setWaterGoalMl(2450);
    profile.setWeeklyRateKg(BigDecimal.ZERO);
    profile.setAllergies(List.of("MILK"));

    when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

    HealthProfileResponse response = profileService.getProfile(userId);

    assertThat(response).isNotNull();
    assertThat(response.userId()).isEqualTo(userId);
    assertThat(response.dailyCalorieGoal()).isEqualTo(2004);
    assertThat(response.allergies()).containsExactly("MILK");
  }

  @Test
  @DisplayName("getProfile() throws PROFILE_NOT_FOUND when not exists")
  void getProfile_notFound() {
    UUID userId = UUID.randomUUID();
    when(profileRepository.findByUserId(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> profileService.getProfile(userId))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PROFILE_NOT_FOUND);
  }

  @Test
  @DisplayName("upsertProfile() creates new profile successfully")
  void upsertProfile_create() {
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setId(userId);

    HealthProfileRequest request =
        new HealthProfileRequest(
            Gender.FEMALE,
            LocalDate.of(2000, 3, 10),
            BigDecimal.valueOf(160.0),
            BigDecimal.valueOf(55.0),
            ActivityLevel.LIGHTLY_ACTIVE,
            GoalType.LOSE_WEIGHT,
            BigDecimal.valueOf(50.0),
            BigDecimal.valueOf(0.5),
            null,
            null,
            null,
            null,
            List.of("PEANUT"));

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(profileRepository.findByUserId(userId)).thenReturn(Optional.empty());
    when(profileRepository.save(any(UserHealthProfile.class)))
        .thenAnswer(inv -> {
          UserHealthProfile p = inv.getArgument(0);
          p.setId(UUID.randomUUID());
          return p;
        });

    HealthProfileResponse response = profileService.upsertProfile(userId, request);

    assertThat(response).isNotNull();
    assertThat(response.gender()).isEqualTo(Gender.FEMALE);
    assertThat(response.allergies()).containsExactly("PEANUT");
    assertThat(response.tdee()).isGreaterThan(0);
    assertThat(response.dailyCalorieGoal()).isGreaterThanOrEqualTo(1200);
    verify(profileRepository).save(any(UserHealthProfile.class));
  }
}
