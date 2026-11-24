package com.healthcare.kallo.service.profile;

import com.healthcare.kallo.dto.request.profile.UpdateUserAllergiesRequest;
import com.healthcare.kallo.dto.response.profile.UserAllergyResponseDto;
import java.util.List;
import java.util.UUID;

public interface AllergyService {
  List<UserAllergyResponseDto> getUserAllergies(UUID userId);

  List<UserAllergyResponseDto> updateUserAllergies(UUID userId, UpdateUserAllergiesRequest request);
}
