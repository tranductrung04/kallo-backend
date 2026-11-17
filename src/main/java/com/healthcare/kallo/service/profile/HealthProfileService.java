package com.healthcare.kallo.service.profile;

import com.healthcare.kallo.dto.request.profile.HealthProfileRequest;
import com.healthcare.kallo.dto.response.profile.HealthProfileResponse;
import java.util.UUID;

public interface HealthProfileService {
  HealthProfileResponse getProfile(UUID userId);

  HealthProfileResponse upsertProfile(UUID userId, HealthProfileRequest request);

  HealthProfileResponse recalculateWithLatestWeight(UUID userId);
}
