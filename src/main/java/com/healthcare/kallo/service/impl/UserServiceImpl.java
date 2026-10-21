package com.healthcare.kallo.service.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.StorageProperties;
import com.healthcare.kallo.dto.request.DeleteAccountRequest;
import com.healthcare.kallo.dto.request.UpdateUserRequest;
import com.healthcare.kallo.dto.response.AvatarResponse;
import com.healthcare.kallo.dto.response.UserProfileResponse;
import com.healthcare.kallo.model.User;
import com.healthcare.kallo.repository.UserRepository;
import com.healthcare.kallo.service.GoogleTokenVerifier;
import com.healthcare.kallo.service.StorageService;
import com.healthcare.kallo.service.UserService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserServiceImpl implements UserService {
  private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
  private static final int MAX_AVATAR_BYTES = 2 * 1024 * 1024;

  private final UserRepository users;
  private final StorageService storage;
  private final StorageProperties storageProperties;
  private final PasswordEncoder passwordEncoder;
  private final GoogleTokenVerifier googleTokenVerifier;

  public UserServiceImpl(
      UserRepository users,
      StorageService storage,
      StorageProperties storageProperties,
      PasswordEncoder passwordEncoder,
      GoogleTokenVerifier googleTokenVerifier) {
    this.users = users;
    this.storage = storage;
    this.storageProperties = storageProperties;
    this.passwordEncoder = passwordEncoder;
    this.googleTokenVerifier = googleTokenVerifier;
  }

  @Override
  @Transactional(readOnly = true)
  public UserProfileResponse getMe(UUID userId) {
    return response(findUser(userId));
  }

  @Override
  @Transactional
  public UserProfileResponse updateMe(UUID userId, UpdateUserRequest request) {
    if (!ZoneId.getAvailableZoneIds().contains(request.timezone())) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
    String displayName = request.displayName().trim();
    if (users.updateProfile(userId, displayName, request.timezone(), Instant.now()) == 0) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
    return response(findUser(userId));
  }

  @Override
  public AvatarResponse updateAvatar(UUID userId, MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_FILE);
    }
    if (file.getSize() > MAX_AVATAR_BYTES) {
      throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
    }
    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.INVALID_FILE);
    }
    String contentType = detectImageType(bytes);
    if (contentType == null) {
      throw new BusinessException(ErrorCode.INVALID_FILE);
    }

    String extension = "image/jpeg".equals(contentType) ? "jpg" : "png";
    String objectKey = "avatars/" + userId + "/" + UUID.randomUUID() + "." + extension;
    storage.upload(objectKey, new ByteArrayInputStream(bytes), bytes.length, contentType);
    String previousKey = findUser(userId).getAvatarUrl();
    if (users.updateAvatarKey(userId, objectKey, Instant.now()) == 0) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
    if (previousKey != null && !previousKey.isBlank()) {
      try {
        storage.delete(previousKey);
      } catch (RuntimeException e) {
        log.warn("Unable to remove replaced avatar {}", previousKey, e);
      }
    }
    return new AvatarResponse(storage.presignedUrl(objectKey, storageProperties.presignedUrlExpiration()));
  }

  @Override
  public void deleteAccount(UUID userId, DeleteAccountRequest request) {
    User user = findUser(userId);
    boolean confirmed = false;
    if (request != null && request.password() != null && user.getPasswordHash() != null) {
      confirmed = passwordEncoder.matches(request.password(), user.getPasswordHash());
    } else if (request != null && request.idToken() != null && user.getPasswordHash() == null) {
      GoogleTokenVerifier.GoogleClaims claims = googleTokenVerifier.verifyFresh(request.idToken());
      confirmed = claims.subject().equals(user.getGoogleSub());
    }
    if (!confirmed) {
      throw new BusinessException(ErrorCode.INVALID_DELETE_CONFIRMATION);
    }
    try {
      storage.deletePrefix("avatars/" + userId + "/");
    } catch (RuntimeException e) {
      log.warn("Unable to remove avatar objects for userId={}", userId, e);
    }
    users.delete(user);
    log.info("Account deleted userId={}", userId);
  }

  private User findUser(UUID userId) {
    return users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
  }

  private UserProfileResponse response(User user) {
    String avatarUrl =
        user.getAvatarUrl() == null
            ? null
            : storage.presignedUrl(user.getAvatarUrl(), storageProperties.presignedUrlExpiration());
    return new UserProfileResponse(
        user.getId(),
        user.getEmail(),
        user.isEmailVerified(),
        user.getDisplayName(),
        user.getTimezone(),
        avatarUrl,
        user.getRole().name());
  }

  private String detectImageType(byte[] bytes) {
    if (bytes.length >= 3
        && (bytes[0] & 0xff) == 0xff
        && (bytes[1] & 0xff) == 0xd8
        && (bytes[2] & 0xff) == 0xff) {
      return "image/jpeg";
    }
    if (bytes.length >= 8
        && (bytes[0] & 0xff) == 0x89
        && bytes[1] == 0x50
        && bytes[2] == 0x4e
        && bytes[3] == 0x47
        && bytes[4] == 0x0d
        && bytes[5] == 0x0a
        && bytes[6] == 0x1a
        && bytes[7] == 0x0a) {
      return "image/png";
    }
    return null;
  }
}
