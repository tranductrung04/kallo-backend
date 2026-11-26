package com.healthcare.kallo.service.food;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.StorageProperties;
import com.healthcare.kallo.service.auth.StorageService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Handles validation, MinIO storage, and presigned URL resolution for food images. */
@Service
public class FoodImageStorageService {

  private static final Logger log = LoggerFactory.getLogger(FoodImageStorageService.class);
  public static final long MAX_IMAGE_BYTES = 2L * 1024 * 1024;
  public static final String FOODS_PREFIX = "foods/";

  private final StorageService storageService;
  private final StorageProperties storageProperties;

  public FoodImageStorageService(
      StorageService storageService, StorageProperties storageProperties) {
    this.storageService = storageService;
    this.storageProperties = storageProperties;
  }

  /**
   * Validates and uploads a food image to MinIO under {@code foods/{userId}/{foodId}/{uuid}.ext}.
   *
   * @return the stored MinIO object key
   */
  public String uploadFoodImage(UUID userId, UUID foodId, MultipartFile file) {
    byte[] bytes = validateAndReadBytes(file);
    String contentType = detectImageMimeType(bytes);
    if (contentType == null) {
      throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }

    String ext = "image/png".equals(contentType) ? "png" : "jpg";
    String objectKey = FOODS_PREFIX + userId + "/" + foodId + "/" + UUID.randomUUID() + "." + ext;
    storageService.upload(objectKey, new ByteArrayInputStream(bytes), bytes.length, contentType);
    return objectKey;
  }

  /** Resolves a stored {@code foods.image_url} value into a client-consumable URL. */
  public String resolveImageUrl(String storedImageUrl) {
    if (storedImageUrl == null || storedImageUrl.isBlank()) {
      return null;
    }
    if (isManagedKey(storedImageUrl)) {
      return storageService.presignedUrl(
          storedImageUrl, storageProperties.presignedUrlExpiration());
    }
    return storedImageUrl;
  }

  /** Deletes the stored MinIO object if {@code storedImageUrl} is a managed food object key. */
  public void deleteIfManaged(String storedImageUrl) {
    if (!isManagedKey(storedImageUrl)) {
      return;
    }
    try {
      storageService.delete(storedImageUrl);
    } catch (RuntimeException e) {
      log.warn("Unable to remove food image {}", storedImageUrl, e);
    }
  }

  public boolean isManagedKey(String value) {
    return value != null && value.startsWith(FOODS_PREFIX);
  }

  /**
   * Returns {@code true} when {@code candidateUrl} looks like a presigned HTTP(S) URL for {@code
   * managedKey}, preventing expiring presigned URLs echoed back on PUT from overwriting the key.
   */
  public boolean isPresignedUrlForKey(String candidateUrl, String managedKey) {
    if (!isManagedKey(managedKey) || candidateUrl == null || candidateUrl.isBlank()) {
      return false;
    }
    try {
      URI uri = URI.create(candidateUrl.trim());
      String scheme = uri.getScheme();
      String path = uri.getPath();
      if (scheme == null || path == null) {
        return false;
      }
      String lowerScheme = scheme.toLowerCase(Locale.ROOT);
      return ("http".equals(lowerScheme) || "https".equals(lowerScheme))
          && path.endsWith("/" + managedKey);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  /** Validates that an imageUrl submitted via JSON is null/blank or a valid HTTP(S) URL. */
  public static String normalizeExternalImageUrl(String imageUrl) {
    if (imageUrl == null || imageUrl.isBlank()) {
      return null;
    }
    String trimmed = imageUrl.trim();
    try {
      URI uri = URI.create(trimmed);
      String scheme = uri.getScheme();
      if (scheme == null || uri.getHost() == null || uri.getHost().isBlank()) {
        throw new BusinessException(ErrorCode.VALIDATION_ERROR);
      }
      String lowerScheme = scheme.toLowerCase(Locale.ROOT);
      if (!"http".equals(lowerScheme) && !"https".equals(lowerScheme)) {
        throw new BusinessException(ErrorCode.VALIDATION_ERROR);
      }
      return trimmed;
    } catch (IllegalArgumentException e) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
  }

  private byte[] validateAndReadBytes(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_FILE);
    }
    if (file.getSize() > MAX_IMAGE_BYTES) {
      throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
    }
    try {
      byte[] bytes = file.getBytes();
      if (bytes.length == 0) {
        throw new BusinessException(ErrorCode.INVALID_FILE);
      }
      if (bytes.length > MAX_IMAGE_BYTES) {
        throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
      }
      return bytes;
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.INVALID_FILE);
    }
  }

  private static String detectImageMimeType(byte[] bytes) {
    if (bytes.length >= 3
        && (bytes[0] & 0xFF) == 0xFF
        && (bytes[1] & 0xFF) == 0xD8
        && (bytes[2] & 0xFF) == 0xFF) {
      return "image/jpeg";
    }
    if (bytes.length >= 8
        && (bytes[0] & 0xFF) == 0x89
        && bytes[1] == 0x50
        && bytes[2] == 0x4E
        && bytes[3] == 0x47
        && bytes[4] == 0x0D
        && bytes[5] == 0x0A
        && bytes[6] == 0x1A
        && bytes[7] == 0x0A) {
      return "image/png";
    }
    return null;
  }
}
