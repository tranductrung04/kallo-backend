package com.healthcare.kallo.service.profile.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.request.profile.UpdateUserAllergiesRequest;
import com.healthcare.kallo.dto.request.profile.UserAllergyItemRequest;
import com.healthcare.kallo.dto.response.profile.UserAllergyResponseDto;
import com.healthcare.kallo.model.profile.UserAllergy;
import com.healthcare.kallo.model.profile.UserAllergyId;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.repository.profile.UserAllergyRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.profile.AllergyService;
import com.healthcare.kallo.service.profile.port.IngredientLookup;
import com.healthcare.kallo.service.profile.port.IngredientSummary;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AllergyServiceImpl implements AllergyService {

  private final UserAllergyRepository allergyRepository;
  private final UserHealthProfileRepository profileRepository;
  private final IngredientLookup ingredientLookup;

  public AllergyServiceImpl(
      UserAllergyRepository allergyRepository,
      UserHealthProfileRepository profileRepository,
      IngredientLookup ingredientLookup) {
    this.allergyRepository = allergyRepository;
    this.profileRepository = profileRepository;
    this.ingredientLookup = ingredientLookup;
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserAllergyResponseDto> getUserAllergies(UUID userId) {
    if (!profileRepository.existsById(userId)) {
      throw new BusinessException(ErrorCode.PROFILE_NOT_FOUND);
    }

    List<UserAllergy> allergies = allergyRepository.findAllByIdUserId(userId);
    if (allergies.isEmpty()) {
      return Collections.emptyList();
    }

    Set<UUID> ids = allergies.stream()
        .map(a -> a.getId().getIngredientId())
        .collect(Collectors.toSet());

    Map<UUID, IngredientSummary> summaries = ingredientLookup.findSummariesByIds(ids);

    return allergies.stream()
        .map(a -> {
          UUID ingId = a.getId().getIngredientId();
          IngredientSummary summary = summaries.get(ingId);
          String name = summary != null ? summary.name() : "Unknown ingredient";
          boolean active = summary != null && summary.active();
          return new UserAllergyResponseDto(ingId, name, a.getNote(), active);
        })
        .toList();
  }

  @Override
  @Transactional
  public List<UserAllergyResponseDto> updateUserAllergies(UUID userId, UpdateUserAllergiesRequest request) {
    UserHealthProfile profile = profileRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND));

    if (request == null || request.allergies() == null || request.allergies().isEmpty()) {
      allergyRepository.deleteAllByUserId(userId);
      return Collections.emptyList();
    }

    Set<UUID> existingIds = allergyRepository.findAllByIdUserId(userId).stream()
        .map(a -> a.getId().getIngredientId())
        .collect(Collectors.toSet());

    Set<UUID> wantedIds = request.allergies().stream()
        .map(UserAllergyItemRequest::ingredientId)
        .collect(Collectors.toSet());

    Map<UUID, IngredientSummary> summaries = ingredientLookup.findSummariesByIds(wantedIds);

    // Validate:
    // 1. All wanted IDs must exist in lookup
    // 2. Newly added ingredients (not in existingIds) must be active
    for (UUID wantedId : wantedIds) {
      IngredientSummary summary = summaries.get(wantedId);
      if (summary == null) {
        throw new BusinessException(ErrorCode.INVALID_INGREDIENT);
      }
      if (!existingIds.contains(wantedId) && !summary.active()) {
        throw new BusinessException(ErrorCode.INVALID_INGREDIENT);
      }
    }

    allergyRepository.deleteAllByUserId(userId);

    List<UserAllergy> newEntities = new ArrayList<>();
    for (UserAllergyItemRequest item : request.allergies()) {
      UserAllergy allergy = new UserAllergy();
      allergy.setId(new UserAllergyId(userId, item.ingredientId()));
      allergy.setProfile(profile);
      allergy.setNote(item.note());
      newEntities.add(allergy);
    }

    allergyRepository.saveAllAndFlush(newEntities);

    return newEntities.stream()
        .map(a -> {
          UUID ingId = a.getId().getIngredientId();
          IngredientSummary summary = summaries.get(ingId);
          String name = summary != null ? summary.name() : "Unknown ingredient";
          boolean active = summary != null && summary.active();
          return new UserAllergyResponseDto(ingId, name, a.getNote(), active);
        })
        .toList();
  }
}
