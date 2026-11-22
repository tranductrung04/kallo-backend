package com.healthcare.kallo.service.auth.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.StorageProperties;
import com.healthcare.kallo.dto.request.auth.DeleteAccountRequest;
import com.healthcare.kallo.dto.request.auth.UpdateUserRequest;
import com.healthcare.kallo.dto.response.auth.AvatarResponse;
import com.healthcare.kallo.dto.response.auth.UserProfileResponse;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.act.WeightLogRepository;
import com.healthcare.kallo.service.auth.GoogleTokenVerifier;
import com.healthcare.kallo.service.auth.StorageService;
import com.healthcare.kallo.service.auth.UserService;
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
  private final WeightLogRepository weightLogs;
  private final StorageService storage;
  private final StorageProperties storageProperties;
  private final PasswordEncoder passwordEncoder;
  private final GoogleTokenVerifier googleTokenVerifier;

  public UserServiceImpl(
      UserRepository users,
      WeightLogRepository weightLogs,
      StorageService storage,
      StorageProperties storageProperties,
      PasswordEncoder passwordEncoder,
      GoogleTokenVerifier googleTokenVerifier) {
    this.users = users;
    this.weightLogs = weightLogs;
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
  public UserProfileResponse updateProfile(UUID userId, com.healthcare.kallo.dto.request.auth.ProfileUpdateRequest request) {
    User user = findUser(userId);
    if (request.gender() != null) {
      String gender = request.gender().trim().toLowerCase(java.util.Locale.ROOT);
      if (!java.util.Set.of("male", "female", "other").contains(gender)) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
      user.setGender(gender);
    }
    if (request.birthDate() != null && request.birthDate().isAfter(java.time.LocalDate.now())) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    if (request.heightCm() != null) user.setHeightCm(request.heightCm());
    if (request.birthDate() != null) user.setBirthDate(request.birthDate());
    if (request.targetWeightKg() != null) user.setTargetWeightKg(request.targetWeightKg());
    if (request.stepGoal() != null) user.setStepGoal(request.stepGoal());
    if (request.weightKg() != null && !weightLogs.existsByUserIdAndDeletedAtIsNull(userId)) {
      weightLogs.insertIfAbsent(userId, java.util.UUID.randomUUID(), request.weightKg(), java.time.Instant.now(), "onboarding");
    }
    return response(users.saveAndFlush(user));
  }

  @Override
  @Transactional
  public UserProfileResponse updateMe(UUID userId, UpdateUserRequest request) {
    if (!ZoneId.getAvailableZoneIds().contains(request.timezone())) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
    String displayName = request.displayName().trim();
    if (request.gender() != null) {
      String normalizedGender = request.gender().trim().toLowerCase(java.util.Locale.ROOT);
      if (!java.util.Set.of("male", "female", "other").contains(normalizedGender)) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
      request = new UpdateUserRequest(request.displayName(), request.timezone(), request.allergies(), request.heightCm(), normalizedGender,
          request.birthDate(), request.targetWeightKg(), request.stepGoal());
    }
    if (request.birthDate() != null && request.birthDate().isAfter(java.time.LocalDate.now())) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    int updated = request.allergies() == null
        ? users.updateProfile(userId, displayName, request.timezone(), Instant.now())
        : users.updateProfileWithAllergies(userId, displayName, request.timezone(), request.allergies(), Instant.now());
    if (request.heightCm() != null || request.gender() != null || request.birthDate() != null
        || request.targetWeightKg() != null || request.stepGoal() != null) {
      User current = findUser(userId);
      var heightCm = request.heightCm() == null ? current.getHeightCm() : request.heightCm();
      var gender = request.gender() == null ? current.getGender() : request.gender();
      var birthDate = request.birthDate() == null ? current.getBirthDate() : request.birthDate();
      var targetWeightKg = request.targetWeightKg() == null ? current.getTargetWeightKg() : request.targetWeightKg();
      var stepGoal = request.stepGoal() == null ? current.getStepGoal() : request.stepGoal();
      updated = request.allergies() == null
          ? users.updateProfileBody(userId, displayName, request.timezone(), heightCm, gender,
              birthDate, targetWeightKg, stepGoal, Instant.now())
          : users.updateProfileWithBodyAndAllergies(userId, displayName, request.timezone(), request.allergies(),
              heightCm, gender, birthDate, targetWeightKg, stepGoal, Instant.now());
    }
    if (updated == 0) {
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
        user.getRole().name(),
        user.getAllergies() == null ? java.util.List.of() : user.getAllergies(),
        weightLogs.findTopByUserIdAndDeletedAtIsNullAndLoggedAtLessThanEqualOrderByLoggedAtDesc(user.getId(), Instant.now())
            .map(com.healthcare.kallo.model.act.WeightLog::getWeightKg).orElse(null),
        user.getHeightCm(), user.getGender(), user.getBirthDate(), user.getTargetWeightKg(), user.getStepGoal());
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
