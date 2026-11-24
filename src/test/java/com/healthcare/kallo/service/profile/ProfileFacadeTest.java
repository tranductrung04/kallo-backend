package com.healthcare.kallo.service.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.healthcare.kallo.dto.request.profile.ProfileRequestDto;
import com.healthcare.kallo.dto.response.profile.ProfileResponseDto;
import com.healthcare.kallo.model.profile.enums.ActivityLevel;
import com.healthcare.kallo.model.profile.enums.Gender;
import com.healthcare.kallo.model.profile.enums.GoalType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class ProfileFacadeTest {

  @Mock private ProfileService profileService;

  private ProfileFacade profileFacade;
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    profileFacade = new ProfileFacade(profileService);
  }

  @Test
  @DisplayName("upsertProfile: khi hồ sơ đã tồn tại thì gọi updateProfile")
  void upsertProfile_existing_callsUpdate() {
    when(profileService.existsByUserId(userId)).thenReturn(true);
    ProfileRequestDto request = dummyRequest();
    ProfileResponseDto expectedResponse = mock(ProfileResponseDto.class);
    when(profileService.updateProfile(userId, request)).thenReturn(expectedResponse);

    ProfileResponseDto result = profileFacade.upsertProfile(userId, request);

    assertThat(result).isSameAs(expectedResponse);
    verify(profileService, never()).createProfile(any(), any());
    verify(profileService).updateProfile(userId, request);
  }

  @Test
  @DisplayName("upsertProfile: khi hồ sơ chưa tồn tại thì gọi createProfile")
  void upsertProfile_new_callsCreate() {
    when(profileService.existsByUserId(userId)).thenReturn(false);
    ProfileRequestDto request = dummyRequest();
    ProfileResponseDto expectedResponse = mock(ProfileResponseDto.class);
    when(profileService.createProfile(userId, request)).thenReturn(expectedResponse);

    ProfileResponseDto result = profileFacade.upsertProfile(userId, request);

    assertThat(result).isSameAs(expectedResponse);
    verify(profileService).createProfile(userId, request);
    verify(profileService, never()).updateProfile(any(), any());
  }

  @Test
  @DisplayName("upsertProfile: khi tranh chấp tạo (DataIntegrityViolationException) thì fallback sang updateProfile")
  void upsertProfile_concurrencyRace_fallsBackToUpdate() {
    when(profileService.existsByUserId(userId)).thenReturn(false);
    ProfileRequestDto request = dummyRequest();
    when(profileService.createProfile(userId, request))
        .thenThrow(new DataIntegrityViolationException("Duplicate key user_health_profiles_pkey"));

    ProfileResponseDto expectedResponse = mock(ProfileResponseDto.class);
    when(profileService.updateProfile(userId, request)).thenReturn(expectedResponse);

    ProfileResponseDto result = profileFacade.upsertProfile(userId, request);

    assertThat(result).isSameAs(expectedResponse);
    verify(profileService).createProfile(userId, request);
    verify(profileService).updateProfile(userId, request);
  }

  private ProfileRequestDto dummyRequest() {
    return new ProfileRequestDto(
        Gender.MALE,
        LocalDate.of(1990, 1, 1),
        new BigDecimal("175.0"),
        new BigDecimal("70.0"),
        ActivityLevel.SEDENTARY,
        GoalType.MAINTAIN_WEIGHT,
        null,
        null
    );
  }
}
