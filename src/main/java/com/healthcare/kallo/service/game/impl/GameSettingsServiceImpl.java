package com.healthcare.kallo.service.game.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.request.game.UpdateGameSettingsRequest;
import com.healthcare.kallo.model.game.UserGameSettings;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.game.UserGameSettingsRepository;
import com.healthcare.kallo.service.game.GameSettingsService;
import java.security.SecureRandom;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameSettingsServiceImpl implements GameSettingsService {
  private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
  private static final int MAX_ATTEMPTS = 5;
  private final UserGameSettingsRepository settings;
  private final UserRepository users;
  private final SecureRandom random = new SecureRandom();

  public GameSettingsServiceImpl(UserGameSettingsRepository settings, UserRepository users) {
    this.settings = settings;
    this.users = users;
  }

  @Override
  @Transactional
  public UserGameSettings getOrCreate(UUID userId) {
    return settings.findById(userId).orElseGet(() -> create(userId));
  }

  @Override
  @Transactional
  public UserGameSettings update(UUID userId, UpdateGameSettingsRequest request) {
    UserGameSettings current = getOrCreate(userId);
    current.setDiscoverable(request.discoverable());
    current.setShareStreak(request.shareStreak());
    current.setSharePoints(request.sharePoints());
    current.setShareSteps(request.shareSteps());
    return current;
  }

  private UserGameSettings create(UUID userId) {
    if (!users.existsById(userId)) throw new BusinessException(ErrorCode.UNAUTHORIZED);
    for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
      if (settings.insertIfAbsent(userId, code()) == 1) {
        return settings.findById(userId).orElseThrow(() -> new IllegalStateException("Game settings could not be loaded"));
      }
      var existing = settings.findById(userId);
      if (existing.isPresent()) return existing.get();
    }
    throw new BusinessException(ErrorCode.INTERNAL_ERROR);
  }

  private String code() {
    StringBuilder value = new StringBuilder(8);
    for (int i = 0; i < 8; i++) value.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
    return value.toString();
  }
}
