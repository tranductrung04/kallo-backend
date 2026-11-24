package com.healthcare.kallo.service.profile;

import com.healthcare.kallo.dto.request.profile.UpdateUserChronicConditionsRequest;
import com.healthcare.kallo.dto.response.profile.ChronicConditionResponseDto;
import com.healthcare.kallo.dto.response.profile.UserChronicConditionResponseDto;
import java.util.List;
import java.util.UUID;

public interface ChronicConditionService {
  List<ChronicConditionResponseDto> getAllActiveConditions();

  List<UserChronicConditionResponseDto> getUserChronicConditions(UUID userId);

  List<UserChronicConditionResponseDto> updateUserChronicConditions(
      UUID userId, UpdateUserChronicConditionsRequest request);
}
