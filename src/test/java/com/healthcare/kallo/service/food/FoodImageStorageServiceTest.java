package com.healthcare.kallo.service.food;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.StorageProperties;
import com.healthcare.kallo.service.auth.StorageService;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class FoodImageStorageServiceTest {

  @Mock private StorageService storageService;

  private FoodImageStorageService service;
  private UUID userId;
  private UUID foodId;

  @BeforeEach
  void setUp() {
    StorageProperties properties =
        new StorageProperties(
            "http://minio:9000",
            "access",
            "secret",
            "kallo-bucket",
            "http://localhost:9000",
            Duration.ofMinutes(15));
    service = new FoodImageStorageService(storageService, properties);
    userId = UUID.randomUUID();
    foodId = UUID.randomUUID();
  }

  @Test
  void uploadsValidJpegUnderFoodsPrefix() {
    var file =
        new MockMultipartFile(
            "file", "pho.jpg", "image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});

    String key = service.uploadFoodImage(userId, foodId, file);

    assertThat(key).startsWith("foods/" + userId + "/" + foodId + "/").endsWith(".jpg");
    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    verify(storageService).upload(keyCaptor.capture(), any(), eq(3L), eq("image/jpeg"));
    assertThat(keyCaptor.getValue()).isEqualTo(key);
  }

  @Test
  void uploadsValidPngUnderFoodsPrefix() {
    var file =
        new MockMultipartFile(
            "file",
            "salad.png",
            "image/png",
            new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});

    String key = service.uploadFoodImage(userId, foodId, file);

    assertThat(key).startsWith("foods/" + userId + "/" + foodId + "/").endsWith(".png");
    verify(storageService).upload(eq(key), any(), eq(8L), eq("image/png"));
  }

  @Test
  void rejectsEmptyUnsupportedAndOversizedFiles() {
    var empty = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);
    assertThatThrownBy(() -> service.uploadFoodImage(userId, foodId, empty))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_FILE);

    var badMagic = new MockMultipartFile("file", "bad.jpg", "image/jpeg", new byte[] {1, 2, 3, 4});
    assertThatThrownBy(() -> service.uploadFoodImage(userId, foodId, badMagic))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.UNSUPPORTED_IMAGE_TYPE);

    var tooLarge =
        new MockMultipartFile(
            "file",
            "huge.jpg",
            "image/jpeg",
            new byte[(int) FoodImageStorageService.MAX_IMAGE_BYTES + 1]);
    assertThatThrownBy(() -> service.uploadFoodImage(userId, foodId, tooLarge))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE);

    verifyNoInteractions(storageService);
  }

  @Test
  void resolvesManagedKeysToPresignedUrlsAndKeepsExternalUrls() {
    String key = "foods/" + userId + "/" + foodId + "/abc.jpg";
    when(storageService.presignedUrl(key, Duration.ofMinutes(15)))
        .thenReturn("http://localhost:9000/kallo-bucket/" + key + "?X-Amz-Signature=sig");

    assertThat(service.resolveImageUrl(null)).isNull();
    assertThat(service.resolveImageUrl("   ")).isNull();
    assertThat(service.resolveImageUrl("https://images.openfoodfacts.org/item.jpg"))
        .isEqualTo("https://images.openfoodfacts.org/item.jpg");
    assertThat(service.resolveImageUrl(key))
        .isEqualTo("http://localhost:9000/kallo-bucket/" + key + "?X-Amz-Signature=sig");
  }

  @Test
  void deletesOnlyManagedFoodKeysAndSwallowsStorageFailures() {
    String key = "foods/" + userId + "/" + foodId + "/abc.jpg";
    doThrow(new IllegalStateException("minio down")).when(storageService).delete(key);

    service.deleteIfManaged("https://cdn.example.com/external.jpg");
    service.deleteIfManaged("avatars/" + userId + "/avatar.jpg");
    verifyNoInteractions(storageService);

    service.deleteIfManaged(key);
    verify(storageService).delete(key);
  }

  @Test
  void detectsPresignedUrlForSameManagedKeyAndValidatesExternalUrls() {
    String key = "foods/" + userId + "/" + foodId + "/abc.jpg";
    String presigned = "http://localhost:9000/kallo-bucket/" + key + "?X-Amz-Expires=900";

    assertThat(service.isPresignedUrlForKey(presigned, key)).isTrue();
    assertThat(service.isPresignedUrlForKey("https://cdn.example.com/other.jpg", key)).isFalse();

    assertThat(FoodImageStorageService.normalizeExternalImageUrl(null)).isNull();
    assertThat(FoodImageStorageService.normalizeExternalImageUrl("  ")).isNull();
    assertThat(FoodImageStorageService.normalizeExternalImageUrl(" https://cdn.kallo.app/a.jpg "))
        .isEqualTo("https://cdn.kallo.app/a.jpg");

    assertThatThrownBy(() -> FoodImageStorageService.normalizeExternalImageUrl("avatars/u1/x.jpg"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
  }
}
