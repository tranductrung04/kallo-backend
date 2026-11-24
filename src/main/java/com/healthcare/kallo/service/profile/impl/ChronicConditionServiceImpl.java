package com.healthcare.kallo.service.profile.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.request.profile.UpdateUserChronicConditionsRequest;
import com.healthcare.kallo.dto.request.profile.UserChronicConditionItemRequest;
import com.healthcare.kallo.dto.response.profile.ChronicConditionResponseDto;
import com.healthcare.kallo.dto.response.profile.UserChronicConditionResponseDto;
import com.healthcare.kallo.model.profile.ChronicCondition;
import com.healthcare.kallo.model.profile.UserChronicCondition;
import com.healthcare.kallo.model.profile.UserChronicConditionId;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.repository.profile.ChronicConditionRepository;
import com.healthcare.kallo.repository.profile.UserChronicConditionRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.profile.ChronicConditionService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChronicConditionServiceImpl implements ChronicConditionService {

  private final ChronicConditionRepository conditionRepository;
  private final UserChronicConditionRepository userConditionRepository;
  private final UserHealthProfileRepository profileRepository;

  public ChronicConditionServiceImpl(
      ChronicConditionRepository conditionRepository,
      UserChronicConditionRepository userConditionRepository,
      UserHealthProfileRepository profileRepository) {
    this.conditionRepository = conditionRepository;
    this.userConditionRepository = userConditionRepository;
    this.profileRepository = profileRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<ChronicConditionResponseDto> getAllActiveConditions() {
    return conditionRepository.findAllByIsActiveTrueOrderByNameAsc().stream()
        .map(c -> new ChronicConditionResponseDto(
            c.getId(),
            c.getCode(),
            c.getName(),
            c.getDescription(),
            c.isActive()
        ))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserChronicConditionResponseDto> getUserChronicConditions(UUID userId) {
    if (!profileRepository.existsById(userId)) {
      throw new BusinessException(ErrorCode.PROFILE_NOT_FOUND);
    }

    return userConditionRepository.findAllByUserIdWithCondition(userId).stream()
        .map(ucc -> new UserChronicConditionResponseDto(
            ucc.getCondition().getId(),
            ucc.getCondition().getCode(),
            ucc.getCondition().getName(),
            ucc.getCondition().getDescription(),
            ucc.getDiagnosedAt(),
            ucc.getNote()
        ))
        .toList();
  }

  @Override
  @Transactional
  public List<UserChronicConditionResponseDto> updateUserChronicConditions(
      UUID userId, UpdateUserChronicConditionsRequest request) {
    UserHealthProfile profile = profileRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND));

    if (request == null || request.conditions() == null || request.conditions().isEmpty()) {
      userConditionRepository.deleteAllByUserId(userId);
      return Collections.emptyList();
    }

    Set<UUID> wantedIds = request.conditions().stream()
        .map(UserChronicConditionItemRequest::conditionId)
        .collect(Collectors.toSet());

    List<ChronicCondition> conditions = conditionRepository.findAllByIdIn(wantedIds);
    if (conditions.size() != wantedIds.size()) {
      throw new BusinessException(ErrorCode.INVALID_CONDITION);
    }

    Map<UUID, ChronicCondition> conditionMap = conditions.stream()
        .collect(Collectors.toMap(ChronicCondition::getId, Function.identity()));

    userConditionRepository.deleteAllByUserId(userId);

    List<UserChronicCondition> newEntities = new ArrayList<>();
    for (UserChronicConditionItemRequest item : request.conditions()) {
      ChronicCondition condition = conditionMap.get(item.conditionId());
      UserChronicCondition ucc = new UserChronicCondition();
      ucc.setId(new UserChronicConditionId(userId, item.conditionId()));
      ucc.setProfile(profile);
      ucc.setCondition(condition);
      ucc.setDiagnosedAt(item.diagnosedAt());
      ucc.setNote(item.note());
      newEntities.add(ucc);
    }

    userConditionRepository.saveAllAndFlush(newEntities);

    return newEntities.stream()
        .map(ucc -> new UserChronicConditionResponseDto(
            ucc.getCondition().getId(),
            ucc.getCondition().getCode(),
            ucc.getCondition().getName(),
            ucc.getCondition().getDescription(),
            ucc.getDiagnosedAt(),
            ucc.getNote()
        ))
        .toList();
  }
}
