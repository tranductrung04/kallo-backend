package com.healthcare.kallo.service.nutri;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.NutriProperties;
import com.healthcare.kallo.config.StorageProperties;
import com.healthcare.kallo.service.auth.StorageService;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class NutriImageStorageService {

  private static final Logger log = LoggerFactory.getLogger(NutriImageStorageService.class);

  private final StorageService storageService;
  private final MinioClient minioClient;
  private final StorageProperties storageProperties;
  private final NutriProperties nutriProperties;

  public NutriImageStorageService(
      StorageService storageService,
      @Qualifier("storageClient") MinioClient minioClient,
      StorageProperties storageProperties,
      NutriProperties nutriProperties) {
    this.storageService = storageService;
    this.minioClient = minioClient;
    this.storageProperties = storageProperties;
    this.nutriProperties = nutriProperties;
  }

  public String uploadTempImage(UUID userId, byte[] bytes, String contentType) {
    String ext = "image/png".equals(contentType) ? "png" : "jpg";
    String key = normalizePrefix(nutriProperties.getStorage().getTempPrefix())
        + userId + "/" + UUID.randomUUID() + "." + ext;
    storageService.upload(key, new ByteArrayInputStream(bytes), bytes.length, contentType);
    return key;
  }

  public String moveTempToMeals(UUID userId, String tempImageKey) {
    if (tempImageKey == null || tempImageKey.isBlank()) {
      return null;
    }
    String expectedPrefix = normalizePrefix(nutriProperties.getStorage().getTempPrefix()) + userId + "/";
    if (!tempImageKey.startsWith(expectedPrefix) || tempImageKey.contains("..")) {
      throw new BusinessException(ErrorCode.INVALID_FILE);
    }

    String ext = tempImageKey.endsWith(".png") ? "png" : "jpg";
    String targetKey = normalizePrefix(nutriProperties.getStorage().getMealsPrefix())
        + userId + "/" + UUID.randomUUID() + "." + ext;

    try {
      minioClient.copyObject(
          CopyObjectArgs.builder()
              .bucket(storageProperties.bucket())
              .object(targetKey)
              .source(CopySource.builder()
                  .bucket(storageProperties.bucket())
                  .object(tempImageKey)
                  .build())
              .build());
    } catch (Exception e) {
      throw new IllegalStateException("Unable to move meal photo from temp storage", e);
    }

    try {
      storageService.delete(tempImageKey);
    } catch (RuntimeException e) {
      log.warn("Failed to remove temporary meal photo {}", tempImageKey, e);
    }
    return targetKey;
  }

  public String presignedUrlOrNull(String objectKey) {
    if (objectKey == null || objectKey.isBlank()) {
      return null;
    }
    return storageService.presignedUrl(objectKey, storageProperties.presignedUrlExpiration());
  }

  public void deleteIfPresent(String objectKey) {
    if (objectKey == null || objectKey.isBlank()) {
      return;
    }
    try {
      storageService.delete(objectKey);
    } catch (RuntimeException e) {
      log.warn("Failed to delete meal photo {}", objectKey, e);
    }
  }

  public int cleanupExpiredTempObjects(Instant cutoff) {
    String prefix = normalizePrefix(nutriProperties.getStorage().getTempPrefix());
    int deleted = 0;
    try {
      Iterable<Result<Item>> results = minioClient.listObjects(
          ListObjectsArgs.builder()
              .bucket(storageProperties.bucket())
              .prefix(prefix)
              .recursive(true)
              .build());
      for (Result<Item> result : results) {
        Item item = result.get();
        if (item.lastModified() != null && item.lastModified().toInstant().isBefore(cutoff)) {
          storageService.delete(item.objectName());
          deleted++;
        }
      }
    } catch (Exception e) {
      log.warn("Failed to cleanup expired temp meal photos", e);
    }
    return deleted;
  }

  private String normalizePrefix(String prefix) {
    String trimmed = prefix == null ? "" : prefix.trim();
    if (trimmed.isEmpty()) {
      return "";
    }
    return trimmed.endsWith("/") ? trimmed : trimmed + "/";
  }
}
