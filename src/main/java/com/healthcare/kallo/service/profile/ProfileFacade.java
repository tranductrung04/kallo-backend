package com.healthcare.kallo.service.profile;

import com.healthcare.kallo.dto.request.profile.ProfileRequestDto;
import com.healthcare.kallo.dto.response.profile.ProfileResponseDto;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class ProfileFacade {

  private final ProfileService profileService;

  public ProfileFacade(ProfileService profileService) {
    this.profileService = profileService;
  }

  public ProfileResponseDto upsertProfile(UUID userId, ProfileRequestDto request) {
    if (profileService.existsByUserId(userId)) {
      return profileService.updateProfile(userId, request);
    }
    try {
      return profileService.createProfile(userId, request);
    } catch (DataIntegrityViolationException ex) {
      // Occurs on concurrent creation attempts with duplicate primary key
      return profileService.updateProfile(userId, request);
    }
  }
}
