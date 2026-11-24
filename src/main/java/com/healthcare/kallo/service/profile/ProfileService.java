package com.healthcare.kallo.service.profile;

import com.healthcare.kallo.dto.request.profile.ProfileRequestDto;
import com.healthcare.kallo.dto.response.profile.ProfileResponseDto;
import java.util.UUID;

public interface ProfileService {
  ProfileResponseDto getProfile(UUID userId);

  boolean existsByUserId(UUID userId);

  ProfileResponseDto createProfile(UUID userId, ProfileRequestDto request);

  ProfileResponseDto updateProfile(UUID userId, ProfileRequestDto request);

  ProfileResponseDto recalculate(UUID userId);
}
