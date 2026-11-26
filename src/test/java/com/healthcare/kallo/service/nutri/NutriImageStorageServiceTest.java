package com.healthcare.kallo.service.nutri;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.NutriProperties;
import com.healthcare.kallo.config.StorageProperties;
import com.healthcare.kallo.service.auth.StorageService;
import io.minio.CopyObjectArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.Result;
import io.minio.messages.Item;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NutriImageStorageServiceTest {

  @Mock private StorageService storageService;
  @Mock private MinioClient minioClient;

  private NutriImageStorageService service;
  private UUID userId;

  @BeforeEach
  void setUp() {
    StorageProperties storageProperties =
        new StorageProperties(
            "http://localhost:9000",
            "key",
            "secret",
            "kallo-bucket",
            "http://localhost:9000",
            Duration.ofMinutes(15));
    service =
        new NutriImageStorageService(
            storageService, minioClient, storageProperties, new NutriProperties());
    userId = UUID.randomUUID();
  }

  @Test
  void moveTempToMealsCopiesToMealsPrefixAndRejectsOtherUsersTempKeys() throws Exception {
    when(minioClient.copyObject(any(CopyObjectArgs.class)))
        .thenReturn(mock(ObjectWriteResponse.class));

    String moved = service.moveTempToMeals(userId, "temp/" + userId + "/dish.jpg");

    assertThat(moved).startsWith("meals/" + userId + "/").endsWith(".jpg");
    verify(minioClient).copyObject(any(CopyObjectArgs.class));
    verify(storageService).delete("temp/" + userId + "/dish.jpg");

    UUID otherUser = UUID.randomUUID();
    assertThatThrownBy(() -> service.moveTempToMeals(userId, "temp/" + otherUser + "/dish.jpg"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_FILE);

    assertThatThrownBy(() -> service.moveTempToMeals(userId, "temp/" + userId + "/../secret.jpg"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_FILE);
  }

  @Test
  @SuppressWarnings("unchecked")
  void cleanupExpiredTempObjectsDeletesOnlyExpiredItems() throws Exception {
    Instant cutoff = Instant.parse("2026-11-24T00:00:00Z");

    Item oldItem = mock(Item.class);
    when(oldItem.objectName()).thenReturn("temp/u1/old.jpg");
    when(oldItem.lastModified()).thenReturn(ZonedDateTime.parse("2026-11-23T12:00:00Z"));

    Item freshItem = mock(Item.class);
    when(freshItem.lastModified()).thenReturn(ZonedDateTime.parse("2026-11-24T12:00:00Z"));

    Result<Item> r1 = mock(Result.class);
    when(r1.get()).thenReturn(oldItem);
    Result<Item> r2 = mock(Result.class);
    when(r2.get()).thenReturn(freshItem);

    when(minioClient.listObjects(any())).thenReturn(List.of(r1, r2));

    int deleted = service.cleanupExpiredTempObjects(cutoff);

    assertThat(deleted).isEqualTo(1);
    verify(storageService).delete("temp/u1/old.jpg");
  }
}
