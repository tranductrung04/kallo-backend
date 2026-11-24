package com.healthcare.kallo.service.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

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
import com.healthcare.kallo.service.profile.impl.ChronicConditionServiceImpl;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChronicConditionServiceImplTest {

  @Mock private ChronicConditionRepository conditionRepository;
  @Mock private UserChronicConditionRepository userConditionRepository;
  @Mock private UserHealthProfileRepository profileRepository;

  private ChronicConditionServiceImpl conditionService;
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    conditionService = new ChronicConditionServiceImpl(
        conditionRepository, userConditionRepository, profileRepository);
  }

  @Test
  @DisplayName("getAllActiveConditions: trả về danh sách bệnh mạn tính đang kích hoạt")
  void getAllActiveConditions() {
    ChronicCondition c1 = new ChronicCondition();
    c1.setId(UUID.randomUUID());
    c1.setCode("DIABETES_T2");
    c1.setName("Tiểu đường tuýp 2");
    c1.setActive(true);

    when(conditionRepository.findAllByIsActiveTrueOrderByNameAsc()).thenReturn(List.of(c1));

    List<ChronicConditionResponseDto> results = conditionService.getAllActiveConditions();

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().code()).isEqualTo("DIABETES_T2");
  }

  @Test
  @DisplayName("updateUserChronicConditions: ném INVALID_CONDITION khi có ID bệnh không tồn tại")
  void updateUserChronicConditions_invalidCondition_throwsError() {
    UserHealthProfile profile = new UserHealthProfile();
    profile.setUserId(userId);
    when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));

    UUID nonExistentId = UUID.randomUUID();
    when(conditionRepository.findAllByIdIn(Set.of(nonExistentId))).thenReturn(List.of()); // Not found

    UpdateUserChronicConditionsRequest request = new UpdateUserChronicConditionsRequest(
        List.of(new UserChronicConditionItemRequest(nonExistentId, LocalDate.now(), "Ghi chú"))
    );

    assertThatThrownBy(() -> conditionService.updateUserChronicConditions(userId, request))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_CONDITION);
  }

  @Test
  @DisplayName("updateUserChronicConditions: cập nhật thành công danh sách bệnh")
  void updateUserChronicConditions_success() {
    UserHealthProfile profile = new UserHealthProfile();
    profile.setUserId(userId);
    when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));

    UUID condId = UUID.randomUUID();
    ChronicCondition condition = new ChronicCondition();
    condition.setId(condId);
    condition.setCode("HYPERTENSION");
    condition.setName("Tăng huyết áp");

    when(conditionRepository.findAllByIdIn(Set.of(condId))).thenReturn(List.of(condition));

    UpdateUserChronicConditionsRequest request = new UpdateUserChronicConditionsRequest(
        List.of(new UserChronicConditionItemRequest(condId, LocalDate.of(2022, 1, 1), "Uống thuốc đều"))
    );

    List<UserChronicConditionResponseDto> results = conditionService.updateUserChronicConditions(userId, request);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().code()).isEqualTo("HYPERTENSION");
    assertThat(results.getFirst().note()).isEqualTo("Uống thuốc đều");
    verify(userConditionRepository).deleteAllByUserId(userId);
    verify(userConditionRepository).saveAllAndFlush(any());
  }
}
