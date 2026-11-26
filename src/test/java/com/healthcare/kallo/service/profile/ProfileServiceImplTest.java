package com.healthcare.kallo.service.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.request.profile.ProfileRequestDto;
import com.healthcare.kallo.dto.response.profile.ProfileResponseDto;
import com.healthcare.kallo.event.profile.ProfileUpdatedEvent;
import com.healthcare.kallo.mapper.ProfileMapper;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.model.profile.enums.ActivityLevel;
import com.healthcare.kallo.model.profile.enums.Gender;
import com.healthcare.kallo.model.profile.enums.GoalType;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.profile.impl.ProfileServiceImpl;
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
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {

  @Mock private UserHealthProfileRepository profileRepository;
  @Mock private UserRepository userRepository;
  @Mock private ApplicationEventPublisher events;

  private ProfileDomainService domainService;
  private Clock clock;
  private ProfileMapper mapper;
  private ProfileServiceImpl profileService;

  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    domainService = new ProfileDomainService();
    clock = Clock.fixed(Instant.parse("2026-11-24T10:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    mapper = new ProfileMapper(domainService, clock);
    profileService = new ProfileServiceImpl(
        profileRepository,
        userRepository,
        domainService,
        mapper,
        events,
        clock
    );
  }

  @Test
  @DisplayName("getProfile: khi không tồn tại hồ sơ thì ném PROFILE_NOT_FOUND")
  void getProfile_notFound() {
    when(profileRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> profileService.getProfile(userId))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.PROFILE_NOT_FOUND);
  }

  @Test
  @DisplayName("createProfile: khi user không tồn tại thì ném NOT_FOUND")
  void createProfile_userNotFound() {
    when(userRepository.existsById(userId)).thenReturn(false);

    ProfileRequestDto request = new ProfileRequestDto(
        Gender.FEMALE,
        LocalDate.of(1995, 8, 15),
        new BigDecimal("162.5"),
        new BigDecimal("58.0"),
        ActivityLevel.MODERATELY_ACTIVE,
        GoalType.LOSE_WEIGHT,
        new BigDecimal("53.0"),
        new BigDecimal("0.50")
    );

    assertThatThrownBy(() -> profileService.createProfile(userId, request))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.NOT_FOUND);
  }

  @Test
  @DisplayName("createProfile: tạo thành công và phát ProfileUpdatedEvent")
  void createProfile_success() {
    when(userRepository.existsById(userId)).thenReturn(true);
    when(profileRepository.saveAndFlush(any(UserHealthProfile.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ProfileRequestDto request = new ProfileRequestDto(
        Gender.FEMALE,
        LocalDate.of(1995, 8, 15),
        new BigDecimal("162.5"),
        new BigDecimal("58.0"),
        ActivityLevel.MODERATELY_ACTIVE,
        GoalType.LOSE_WEIGHT,
        new BigDecimal("53.0"),
        new BigDecimal("0.50"),
        10000
    );

    ProfileResponseDto response = profileService.createProfile(userId, request);

    assertThat(response).isNotNull();
    assertThat(response.userId()).isEqualTo(userId);
    assertThat(response.weightKg()).isEqualByComparingTo("58.0");
    assertThat(response.gender()).isEqualTo(Gender.FEMALE);
    assertThat(response.goalType()).isEqualTo(GoalType.LOSE_WEIGHT);
    assertThat(response.stepGoal()).isEqualTo(10000);

    verify(events).publishEvent(any(ProfileUpdatedEvent.class));
  }

  @Test
  @DisplayName("recalculate: tự động chuyển MAINTAIN_WEIGHT khi cân nặng đã đạt mục tiêu giảm cân")
  void recalculate_targetReached_loseWeight() {
    UserHealthProfile profile = new UserHealthProfile();
    profile.setUserId(userId);
    profile.setGender(Gender.FEMALE);
    profile.setDob(LocalDate.of(1993, 12, 12));
    profile.setHeightCm(new BigDecimal("165.0"));
    profile.setWeightKg(new BigDecimal("65.0"));
    profile.setLatestLoggedWeightKg(new BigDecimal("60.0")); // latest logged reached target 60.0
    profile.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE);
    profile.setGoalType(GoalType.LOSE_WEIGHT);
    profile.setTargetWeightKg(new BigDecimal("60.0"));
    profile.setWeeklyRateKg(new BigDecimal("0.50"));
    profile.setRecalculationRecommended(true);

    when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));
    when(profileRepository.saveAndFlush(any(UserHealthProfile.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ProfileResponseDto response = profileService.recalculate(userId);

    assertThat(response.goalAdvice()).isEqualTo("TARGET_REACHED");
    assertThat(response.goalType()).isEqualTo(GoalType.MAINTAIN_WEIGHT);
    assertThat(response.targetWeightKg()).isNull();
    assertThat(response.weeklyRateKg()).isEqualByComparingTo("0.00");
    assertThat(response.weightKg()).isEqualByComparingTo("60.0");
    assertThat(response.recalculationRecommended()).isFalse();

    verify(events).publishEvent(any(ProfileUpdatedEvent.class));
  }
}
