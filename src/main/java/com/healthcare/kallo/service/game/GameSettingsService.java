package com.healthcare.kallo.service.game;

import com.healthcare.kallo.model.game.UserGameSettings;
import com.healthcare.kallo.dto.request.game.UpdateGameSettingsRequest;
import java.util.UUID;

public interface GameSettingsService {
  UserGameSettings getOrCreate(UUID userId);
  UserGameSettings update(UUID userId, UpdateGameSettingsRequest request);
}
