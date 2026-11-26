package com.healthcare.kallo.service.food;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.StorageProperties;
import com.healthcare.kallo.dto.request.food.CustomFoodRequest;
import com.healthcare.kallo.dto.response.food.FoodResponse;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.model.food.enums.FoodSource;
import com.healthcare.kallo.repository.food.FoodIngredientRepository;
import com.healthcare.kallo.repository.food.FoodRepository;
import com.healthcare.kallo.repository.food.IngredientRepository;
import com.healthcare.kallo.service.auth.StorageService;
import com.healthcare.kallo.service.food.impl.FoodServiceImpl;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class FoodServiceImageTest {

  @Mock private FoodRepository foodRepository;
  @Mock private IngredientRepository ingredientRepository;
  @Mock private FoodIngredientRepository foodIngredientRepository;
  @Mock private StorageService storageService;

  private FoodServiceImpl foodService;
  private UUID userId;
  private UUID foodId;

  @BeforeEach
  void setUp() {
    StorageProperties storageProperties =
        new StorageProperties(
            "http://minio:9000",
            "access",
            "secret",
            "kallo-bucket",
            "http://localhost:9000",
            Duration.ofMinutes(15));
    FoodImageStorageService imageStorage =
        new FoodImageStorageService(storageService, storageProperties);
    foodService =
        new FoodServiceImpl(
            foodRepository,
            ingredientRepository,
            foodIngredientRepository,
            new FoodNutritionCalculator(),
            imageStorage);
    userId = UUID.randomUUID();
    foodId = UUID.randomUUID();
  }

  @Test
  void uploadFoodImageStoresObjectKeyDeletesPreviousManagedKeyAndReturnsPresignedUrl() {
    String oldKey = "foods/" + userId + "/" + foodId + "/old.jpg";
    Food food = customFood(userId, oldKey);
    when(foodRepository.findByIdWithIngredients(foodId)).thenReturn(Optional.of(food));
    when(foodRepository.save(any(Food.class))).thenAnswer(inv -> inv.getArgument(0));
    when(storageService.presignedUrl(anyString(), eq(Duration.ofMinutes(15))))
        .thenAnswer(inv -> "http://localhost:9000/kallo-bucket/" + inv.getArgument(0) + "?sig=1");

    var file =
        new MockMultipartFile(
            "file", "dish.jpg", "image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});

    FoodResponse response = foodService.uploadFoodImage(userId, foodId, file);

    assertThat(food.getImageUrl())
        .startsWith("foods/" + userId + "/" + foodId + "/")
        .endsWith(".jpg")
        .isNotEqualTo(oldKey);
    assertThat(response.imageUrl())
        .isEqualTo("http://localhost:9000/kallo-bucket/" + food.getImageUrl() + "?sig=1");
    verify(storageService).upload(eq(food.getImageUrl()), any(), eq(3L), eq("image/jpeg"));
    verify(storageService).delete(oldKey);
  }

  @Test
  void uploadFoodImageCleansUpNewObjectWhenDatabaseSaveFails() {
    Food food = customFood(userId, null);
    when(foodRepository.findByIdWithIngredients(foodId)).thenReturn(Optional.of(food));
    when(foodRepository.save(any(Food.class))).thenThrow(new IllegalStateException("db failed"));

    var file =
        new MockMultipartFile(
            "file", "dish.jpg", "image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});

    assertThatThrownBy(() -> foodService.uploadFoodImage(userId, foodId, file))
        .isInstanceOf(IllegalStateException.class);

    verify(storageService).upload(anyString(), any(), anyLong(), eq("image/jpeg"));
    verify(storageService).delete(food.getImageUrl());
  }

  @Test
  void uploadFoodImageRejectsSystemFoodAndOtherUsersFood() {
    Food systemFood = new Food();
    systemFood.setId(foodId);
    systemFood.setSource(FoodSource.SYSTEM);
    when(foodRepository.findByIdWithIngredients(foodId)).thenReturn(Optional.of(systemFood));

    var file =
        new MockMultipartFile(
            "file", "dish.jpg", "image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});

    assertThatThrownBy(() -> foodService.uploadFoodImage(userId, foodId, file))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.CANNOT_MODIFY_SYSTEM_FOOD);

    Food otherUsersFood = customFood(UUID.randomUUID(), null);
    when(foodRepository.findByIdWithIngredients(foodId)).thenReturn(Optional.of(otherUsersFood));

    assertThatThrownBy(() -> foodService.uploadFoodImage(userId, foodId, file))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.FORBIDDEN_FOOD_ACCESS);

    verifyNoInteractions(storageService);
  }

  @Test
  void deleteFoodImageClearsDatabaseFieldAndRemovesManagedObject() {
    String existingKey = "foods/" + userId + "/" + foodId + "/pic.png";
    Food food = customFood(userId, existingKey);
    when(foodRepository.findById(foodId)).thenReturn(Optional.of(food));

    foodService.deleteFoodImage(userId, foodId);

    assertThat(food.getImageUrl()).isNull();
    verify(foodRepository).save(food);
    verify(storageService).delete(existingKey);
  }

  @Test
  void updateCustomFoodPreservesManagedKeyWhenImageUrlOmittedOrPresignedUrlEchoed() {
    String existingKey = "foods/" + userId + "/" + foodId + "/pic.jpg";
    Food food = customFood(userId, existingKey);
    when(foodRepository.findByIdWithIngredients(foodId)).thenReturn(Optional.of(food));
    when(foodRepository.save(any(Food.class))).thenAnswer(inv -> inv.getArgument(0));
    when(storageService.presignedUrl(existingKey, Duration.ofMinutes(15)))
        .thenReturn("http://localhost:9000/kallo-bucket/" + existingKey + "?X-Amz-Signature=abc");

    CustomFoodRequest omitImageReq =
        new CustomFoodRequest(
            "Salad mới",
            FoodCategory.VEGETABLE,
            "1 đĩa",
            new BigDecimal("200.0"),
            new BigDecimal("80.0"),
            new BigDecimal("5.0"),
            new BigDecimal("10.0"),
            new BigDecimal("2.0"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            List.of("LUNCH"),
            null);

    FoodResponse res1 = foodService.updateCustomFood(userId, foodId, omitImageReq);
    assertThat(food.getImageUrl()).isEqualTo(existingKey);
    assertThat(res1.imageUrl()).contains(existingKey);
    verify(storageService, never()).delete(anyString());

    CustomFoodRequest echoPresignedReq =
        new CustomFoodRequest(
            "Salad mới 2",
            FoodCategory.VEGETABLE,
            "1 đĩa",
            new BigDecimal("200.0"),
            new BigDecimal("80.0"),
            new BigDecimal("5.0"),
            new BigDecimal("10.0"),
            new BigDecimal("2.0"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            List.of("LUNCH"),
            "http://localhost:9000/kallo-bucket/" + existingKey + "?X-Amz-Signature=abc");

    foodService.updateCustomFood(userId, foodId, echoPresignedReq);
    assertThat(food.getImageUrl()).isEqualTo(existingKey);
    verify(storageService, never()).delete(anyString());
  }

  @Test
  void createCustomFoodRejectsNonHttpImageUrlToPreventObjectKeyInjection() {
    CustomFoodRequest badRequest =
        new CustomFoodRequest(
            "Món thử",
            FoodCategory.OTHER,
            "1 phần",
            new BigDecimal("100.0"),
            new BigDecimal("100.0"),
            new BigDecimal("10.0"),
            new BigDecimal("10.0"),
            new BigDecimal("2.0"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            List.of("LUNCH"),
            "avatars/victim/secret.jpg");

    assertThatThrownBy(() -> foodService.createCustomFood(userId, badRequest))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
  }

  private Food customFood(UUID ownerId, String imageUrl) {
    Food food = new Food();
    food.setId(foodId);
    food.setName("Cơm gà");
    food.setCategory(FoodCategory.RICE);
    food.setDefaultServingName("1 đĩa");
    food.setDefaultServingGrams(new BigDecimal("300.0"));
    food.setSource(FoodSource.USER_CUSTOM);
    food.setOwnerUserId(ownerId);
    food.setImageUrl(imageUrl);
    return food;
  }
}
