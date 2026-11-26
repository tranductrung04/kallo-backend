package com.healthcare.kallo.service.nutri.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.NutriProperties;
import com.healthcare.kallo.dto.response.nutri.FoodRecognitionResponse;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.repository.food.FoodRepository;
import com.healthcare.kallo.repository.profile.UserAllergyRepository;
import com.healthcare.kallo.service.nutri.NutriAllergyChecker;
import com.healthcare.kallo.service.nutri.NutriImageStorageService;
import com.healthcare.kallo.service.nutri.client.FoodAiClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FoodRecognitionServiceImplTest {

  private static final byte[] JPEG_BYTES = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
  private static final byte[] PNG_BYTES =
      new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01};

  @Mock private FoodAiClient foodAiClient;
  @Mock private NutriImageStorageService imageStorageService;
  @Mock private FoodRepository foodRepository;
  @Mock private UserAllergyRepository userAllergyRepository;

  private NutriProperties nutriProperties;
  private FoodRecognitionServiceImpl service;
  private UUID userId;

  @BeforeEach
  void setUp() {
    nutriProperties = new NutriProperties();
    nutriProperties.getAi().setConfidenceThreshold(new BigDecimal("0.45"));
    service =
        new FoodRecognitionServiceImpl(
            foodAiClient,
            imageStorageService,
            foodRepository,
            new NutriAllergyChecker(userAllergyRepository),
            nutriProperties);
    userId = UUID.randomUUID();
  }

  @Test
  void recognizeNormalizesLabelsDeduplicatesAndPerformsBatchLookup() {
    String tempKey = "temp/" + userId + "/sample.jpg";
    when(imageStorageService.uploadTempImage(userId, JPEG_BYTES, "image/jpeg")).thenReturn(tempKey);
    when(imageStorageService.presignedUrlOrNull(tempKey)).thenReturn("https://minio/sample.jpg");
    when(foodAiClient.detect(JPEG_BYTES, "meal.jpg", "image/jpeg"))
        .thenReturn(
            new FoodAiClient.FoodAiDetectResponse(
                true,
                "Banh mi",
                0.954,
                List.of(
                    new FoodAiClient.DetectedItem("Con nguoi", 0.99),
                    new FoodAiClient.DetectedItem("Pho bo", 0.30),
                    new FoodAiClient.DetectedItem("Banh mi", 0.954),
                    new FoodAiClient.DetectedItem("banh_mi", 0.72),
                    new FoodAiClient.DetectedItem("Sup cua", 0.88))));

    Food banhMi = food(UUID.randomUUID(), "Bánh mì", 4, 450);
    Food supCua = food(UUID.randomUUID(), "Súp cua", 67, 205);
    when(foodRepository.findActiveSystemFoodsByNameUnaccentedIn(List.of("banh mi", "sup cua")))
        .thenReturn(List.of(banhMi, supCua));
    when(userAllergyRepository.findAllByIdUserId(userId)).thenReturn(List.of());

    MockMultipartFile file = new MockMultipartFile("image", "meal.jpg", "image/jpeg", JPEG_BYTES);
    FoodRecognitionResponse res = service.recognize(userId, file);

    assertThat(res.tempImageKey()).isEqualTo(tempKey);
    assertThat(res.modelVersion()).isEqualTo("modal-food-scanner");
    assertThat(res.items()).hasSize(2);
    assertThat(res.items().get(0).foodId()).isEqualTo(banhMi.getId());
    assertThat(res.items().get(0).foodName()).isEqualTo("Bánh mì");
    assertThat(res.items().get(0).classId()).isEqualTo(4);
    assertThat(res.items().get(0).confidence()).isEqualTo(0.954);
    assertThat(res.items().get(1).foodId()).isEqualTo(supCua.getId());
    assertThat(res.items().get(1).classId()).isEqualTo(67);
    verify(foodRepository, times(1)).findActiveSystemFoodsByNameUnaccentedIn(anyCollection());
    verify(foodRepository, never()).findBestSystemMatchByUnaccentedLabel(any());
  }

  @Test
  void recognizeFallsBackToSimilarityWhenExactBatchMatchMissingAndHandlesUnmatched() {
    String tempKey = "temp/" + userId + "/fallback.jpg";
    when(imageStorageService.uploadTempImage(userId, JPEG_BYTES, "image/jpeg")).thenReturn(tempKey);
    when(imageStorageService.presignedUrlOrNull(tempKey)).thenReturn("https://minio/fallback.jpg");
    when(foodAiClient.detect(JPEG_BYTES, "meal.jpg", "image/jpeg"))
        .thenReturn(
            new FoodAiClient.FoodAiDetectResponse(
                true,
                "Pho bo",
                0.91,
                List.of(
                    new FoodAiClient.DetectedItem("Pho bo", 0.91),
                    new FoodAiClient.DetectedItem("Unknown dish", 0.70))));

    Food pho = food(UUID.randomUUID(), "Phở", 46, 450);
    when(foodRepository.findActiveSystemFoodsByNameUnaccentedIn(List.of("pho bo", "unknown dish")))
        .thenReturn(List.of());
    when(foodRepository.findBestSystemMatchByUnaccentedLabel("pho bo")).thenReturn(Optional.of(pho));
    when(foodRepository.findBestSystemMatchByUnaccentedLabel("unknown dish"))
        .thenReturn(Optional.empty());
    when(userAllergyRepository.findAllByIdUserId(userId)).thenReturn(List.of());

    MockMultipartFile file = new MockMultipartFile("image", "meal.jpg", "image/jpeg", JPEG_BYTES);
    FoodRecognitionResponse res = service.recognize(userId, file);

    assertThat(res.items()).hasSize(2);
    assertThat(res.items().get(0).foodId()).isEqualTo(pho.getId());
    assertThat(res.items().get(0).foodName()).isEqualTo("Phở");
    assertThat(res.items().get(1).foodId()).isNull();
    assertThat(res.items().get(1).foodName()).isEqualTo("Unknown dish");
    assertThat(res.items().get(1).classId()).isNull();
  }

  @Test
  void recognizeDeletesTempPhotoAndThrowsNonFoodDetectedWhenNotDetectedOrOnlyHuman() {
    String tempKey = "temp/" + userId + "/person.png";
    when(imageStorageService.uploadTempImage(userId, PNG_BYTES, "image/png")).thenReturn(tempKey);
    when(foodAiClient.detect(PNG_BYTES, "meal.png", "image/png"))
        .thenReturn(
            new FoodAiClient.FoodAiDetectResponse(
                true,
                "Con nguoi",
                0.95,
                List.of(new FoodAiClient.DetectedItem("Con nguoi", 0.95))));

    MockMultipartFile file = new MockMultipartFile("image", "person.png", "image/png", PNG_BYTES);
    assertThatThrownBy(() -> service.recognize(userId, file))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NON_FOOD_DETECTED);

    verify(imageStorageService).deleteIfPresent(tempKey);
  }

  @Test
  void recognizeRejectsUnsupportedImageTypeAndOversizedImage() {
    MockMultipartFile fakeText =
        new MockMultipartFile("image", "not-image.jpg", "image/jpeg", "hello-world".getBytes());
    assertThatThrownBy(() -> service.recognize(userId, fakeText))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.UNSUPPORTED_IMAGE_TYPE);

    byte[] oversized = new byte[(int) nutriProperties.getMaxImageBytes() + 1];
    MockMultipartFile bigFile =
        new MockMultipartFile("image", "big.jpg", "image/jpeg", oversized);
    assertThatThrownBy(() -> service.recognize(userId, bigFile))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE);

    verify(foodAiClient, never()).detect(any(), any(), any());
  }

  @Test
  void recognizeIgnoresSoftDeletedCatalogFood() {
    String tempKey = "temp/" + userId + "/deleted.jpg";
    when(imageStorageService.uploadTempImage(userId, JPEG_BYTES, "image/jpeg")).thenReturn(tempKey);
    when(foodAiClient.detect(JPEG_BYTES, "meal.jpg", "image/jpeg"))
        .thenReturn(new FoodAiClient.FoodAiDetectResponse(true, "Banh mi", 0.90, List.of()));
    Food deleted = food(UUID.randomUUID(), "Bánh mì", 4, 450);
    deleted.setDeletedAt(Instant.now());
    when(foodRepository.findActiveSystemFoodsByNameUnaccentedIn(List.of("banh mi")))
        .thenReturn(List.of(deleted));
    when(foodRepository.findBestSystemMatchByUnaccentedLabel("banh mi"))
        .thenReturn(Optional.of(deleted));
    when(userAllergyRepository.findAllByIdUserId(userId)).thenReturn(List.of());

    MockMultipartFile file = new MockMultipartFile("image", "meal.jpg", "image/jpeg", JPEG_BYTES);
    FoodRecognitionResponse res = service.recognize(userId, file);

    assertThat(res.items()).hasSize(1);
    assertThat(res.items().get(0).foodId()).isNull();
    assertThat(res.items().get(0).caloriesPerServing()).isNull();
  }

  private Food food(UUID id, String name, Integer vietfoodClassId, int caloriesPerServing) {
    Food food = new Food();
    ReflectionTestUtils.setField(food, "id", id);
    food.setName(name);
    food.setVietfoodClassId(vietfoodClassId);
    food.setDefaultServingName("1 phần");
    food.setDefaultServingGrams(new BigDecimal("200.0"));
    food.setCaloriesPerServing(caloriesPerServing);
    food.setProteinPerServing(new BigDecimal("18.0"));
    food.setCarbsPerServing(new BigDecimal("45.0"));
    food.setFatPerServing(new BigDecimal("10.0"));
    return food;
  }
}
