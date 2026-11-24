package com.healthcare.kallo.service.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

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
import com.healthcare.kallo.service.profile.impl.AllergyServiceImpl;
import com.healthcare.kallo.service.profile.port.IngredientLookup;
import com.healthcare.kallo.service.profile.port.IngredientSummary;
import java.util.List;
import java.util.Map;
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
class AllergyServiceImplTest {

  @Mock private UserAllergyRepository allergyRepository;
  @Mock private UserHealthProfileRepository profileRepository;
  @Mock private IngredientLookup ingredientLookup;

  private AllergyServiceImpl allergyService;
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    allergyService = new AllergyServiceImpl(allergyRepository, profileRepository, ingredientLookup);
  }

  @Test
  @DisplayName("getUserAllergies: hiển thị đầy đủ thông tin kể cả nguyên liệu đã bị inactive")
  void getUserAllergies_displaysAllIncludingInactive() {
    when(profileRepository.existsById(userId)).thenReturn(true);

    UUID ingId1 = UUID.randomUUID();
    UUID ingId2 = UUID.randomUUID();

    UserAllergy allergy1 = new UserAllergy();
    allergy1.setId(new UserAllergyId(userId, ingId1));
    allergy1.setNote("Nhẹ");

    UserAllergy allergy2 = new UserAllergy();
    allergy2.setId(new UserAllergyId(userId, ingId2));
    allergy2.setNote("Nặng");

    when(allergyRepository.findAllByIdUserId(userId)).thenReturn(List.of(allergy1, allergy2));

    when(ingredientLookup.findSummariesByIds(Set.of(ingId1, ingId2))).thenReturn(Map.of(
        ingId1, new IngredientSummary(ingId1, "Peanuts", true),
        ingId2, new IngredientSummary(ingId2, "Inactive Allergen", false)
    ));

    List<UserAllergyResponseDto> results = allergyService.getUserAllergies(userId);

    assertThat(results).hasSize(2);
    assertThat(results).extracting(UserAllergyResponseDto::name).containsExactlyInAnyOrder("Peanuts", "Inactive Allergen");
    assertThat(results).extracting(UserAllergyResponseDto::active).containsExactlyInAnyOrder(true, false);
  }

  @Test
  @DisplayName("updateUserAllergies: rỗng thì xóa sạch và không gọi lookup")
  void updateUserAllergies_empty_clearsAllWithoutLookup() {
    UserHealthProfile profile = new UserHealthProfile();
    profile.setUserId(userId);
    when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));

    List<UserAllergyResponseDto> result = allergyService.updateUserAllergies(
        userId, new UpdateUserAllergiesRequest(List.of()));

    assertThat(result).isEmpty();
    verify(allergyRepository).deleteAllByUserId(userId);
    verifyNoInteractions(ingredientLookup);
  }

  @Test
  @DisplayName("updateUserAllergies: chặn lỗi INVALID_INGREDIENT khi nguyên liệu mới thêm vào bị inactive")
  void updateUserAllergies_newInactiveIngredient_throwsError() {
    UserHealthProfile profile = new UserHealthProfile();
    profile.setUserId(userId);
    when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));

    UUID newIngId = UUID.randomUUID();
    when(allergyRepository.findAllByIdUserId(userId)).thenReturn(List.of()); // no existing allergies
    when(ingredientLookup.findSummariesByIds(Set.of(newIngId)))
        .thenReturn(Map.of(newIngId, new IngredientSummary(newIngId, "Old Inactive Nut", false)));

    UpdateUserAllergiesRequest request = new UpdateUserAllergiesRequest(
        List.of(new UserAllergyItemRequest(newIngId, "Dị ứng"))
    );

    assertThatThrownBy(() -> allergyService.updateUserAllergies(userId, request))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_INGREDIENT);
  }

  @Test
  @DisplayName("updateUserAllergies: nguyên liệu đã tồn tại từ trước dù inactive vẫn cho phép giữ lại")
  void updateUserAllergies_existingInactiveIngredient_allowed() {
    UserHealthProfile profile = new UserHealthProfile();
    profile.setUserId(userId);
    when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));

    UUID existingInactiveId = UUID.randomUUID();
    UserAllergy existingAllergy = new UserAllergy();
    existingAllergy.setId(new UserAllergyId(userId, existingInactiveId));

    when(allergyRepository.findAllByIdUserId(userId)).thenReturn(List.of(existingAllergy));
    when(ingredientLookup.findSummariesByIds(Set.of(existingInactiveId)))
        .thenReturn(Map.of(existingInactiveId, new IngredientSummary(existingInactiveId, "Existing Inactive", false)));

    UpdateUserAllergiesRequest request = new UpdateUserAllergiesRequest(
        List.of(new UserAllergyItemRequest(existingInactiveId, "Ghi chú mới"))
    );

    List<UserAllergyResponseDto> result = allergyService.updateUserAllergies(userId, request);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().name()).isEqualTo("Existing Inactive");
    assertThat(result.getFirst().active()).isFalse();
    verify(allergyRepository).deleteAllByUserId(userId);
    verify(allergyRepository).saveAllAndFlush(any());
  }
}
