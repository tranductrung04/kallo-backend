package com.healthcare.kallo.service.food;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.request.food.CustomIngredientRequest;
import com.healthcare.kallo.dto.response.food.IngredientResponse;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.Ingredient;
import com.healthcare.kallo.model.food.enums.FoodSource;
import com.healthcare.kallo.repository.food.FoodRepository;
import com.healthcare.kallo.repository.food.IngredientRepository;
import com.healthcare.kallo.service.food.impl.IngredientServiceImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngredientServiceTest {

  @Mock private IngredientRepository ingredientRepository;
  @Mock private FoodRepository foodRepository;
  @Mock private FoodNutritionCalculator nutritionCalculator;

  private IngredientServiceImpl ingredientService;
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    ingredientService = new IngredientServiceImpl(ingredientRepository, foodRepository, nutritionCalculator);
  }

  @Test
  @DisplayName("testCreateCustomIngredient: tạo nguyên liệu cá nhân thành công với source USER_CUSTOM")
  void testCreateCustomIngredient() {
    CustomIngredientRequest request = new CustomIngredientRequest(
        "Sốt mè tự làm",
        new BigDecimal("350.0"),
        new BigDecimal("8.0"),
        new BigDecimal("15.0"),
        new BigDecimal("30.0"),
        new BigDecimal("5.0"),
        new BigDecimal("2.0")
    );

    when(ingredientRepository.countByOwnerUserIdAndCreatedAtAfter(eq(userId), any(Instant.class))).thenReturn(5L);
    when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(invocation -> {
      Ingredient entity = invocation.getArgument(0);
      entity.setId(UUID.randomUUID());
      return entity;
    });

    IngredientResponse response = ingredientService.createCustomIngredient(userId, request);

    assertThat(response).isNotNull();
    assertThat(response.name()).isEqualTo("Sốt mè tự làm");
    assertThat(response.source()).isEqualTo(FoodSource.USER_CUSTOM);
    assertThat(response.ownerUserId()).isEqualTo(userId);
    assertThat(response.isAllergen()).isFalse();
    assertThat(response.isActive()).isTrue();
  }

  @Test
  @DisplayName("testMacroSumValidation: tổng protein + carbs + fat vượt quá 105g thì ném VALIDATION_ERROR")
  void testMacroSumValidation() {
    CustomIngredientRequest request = new CustomIngredientRequest(
        "Bột protein đậm đặc",
        new BigDecimal("500.0"),
        new BigDecimal("80.0"),
        new BigDecimal("20.0"),
        new BigDecimal("10.0"), // total = 110.0 > 105.0
        BigDecimal.ZERO,
        BigDecimal.ZERO
    );

    assertThatThrownBy(() -> ingredientService.createCustomIngredient(userId, request))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
  }

  @Test
  @DisplayName("testAllergenSearchIsolation: tìm kiếm với allergenOnly=true gọi repository với flag chính xác")
  void testAllergenSearchIsolation() {
    Ingredient ing = new Ingredient();
    ing.setId(UUID.randomUUID());
    ing.setName("Lạc");
    ing.setAllergen(true);
    ing.setSource(FoodSource.SYSTEM);
    ing.setActive(true);

    when(ingredientRepository.searchIngredients(userId, null, true)).thenReturn(List.of(ing));

    List<IngredientResponse> results = ingredientService.searchIngredients(userId, null, true);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).isAllergen()).isTrue();
    assertThat(results.get(0).source()).isEqualTo(FoodSource.SYSTEM);
  }

  @Test
  @DisplayName("testSoftDeleteCustomIngredient: xóa nguyên liệu cá nhân chuyển isActive sang false")
  void testSoftDeleteCustomIngredient() {
    UUID ingId = UUID.randomUUID();
    Ingredient ing = new Ingredient();
    ing.setId(ingId);
    ing.setName("Sốt tự làm");
    ing.setSource(FoodSource.USER_CUSTOM);
    ing.setOwnerUserId(userId);
    ing.setActive(true);

    when(ingredientRepository.findById(ingId)).thenReturn(Optional.of(ing));

    ingredientService.deleteCustomIngredient(userId, ingId);

    assertThat(ing.isActive()).isFalse();
    verify(ingredientRepository).save(ing);
  }

  @Test
  @DisplayName("testPreventModifyingSystemIngredient: không được phép sửa nguyên liệu hệ thống")
  void testPreventModifyingSystemIngredient() {
    UUID ingId = UUID.randomUUID();
    Ingredient sysIng = new Ingredient();
    sysIng.setId(ingId);
    sysIng.setName("Trứng");
    sysIng.setSource(FoodSource.SYSTEM);
    sysIng.setOwnerUserId(null);

    when(ingredientRepository.findById(ingId)).thenReturn(Optional.of(sysIng));

    CustomIngredientRequest request = new CustomIngredientRequest(
        "Trứng gà ta", new BigDecimal("150"), new BigDecimal("12"), new BigDecimal("1"), new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO
    );

    assertThatThrownBy(() -> ingredientService.updateCustomIngredient(userId, ingId, request))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CANNOT_MODIFY_SYSTEM_INGREDIENT);
  }

  @Test
  @DisplayName("testPreventIDOROnIngredient: User A không được sửa nguyên liệu của User B")
  void testPreventIDOROnIngredient() {
    UUID ingId = UUID.randomUUID();
    UUID userB = UUID.randomUUID();
    Ingredient ing = new Ingredient();
    ing.setId(ingId);
    ing.setName("Sốt bí mật");
    ing.setSource(FoodSource.USER_CUSTOM);
    ing.setOwnerUserId(userB);

    when(ingredientRepository.findById(ingId)).thenReturn(Optional.of(ing));

    CustomIngredientRequest request = new CustomIngredientRequest(
        "Sốt bí mật sửa", new BigDecimal("150"), new BigDecimal("5"), new BigDecimal("10"), new BigDecimal("5"), BigDecimal.ZERO, BigDecimal.ZERO
    );

    assertThatThrownBy(() -> ingredientService.updateCustomIngredient(userId, ingId, request))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.FORBIDDEN_INGREDIENT_ACCESS);
  }
}
