package com.healthcare.kallo.dto.response.game;

import com.healthcare.kallo.model.game.UserGameSettings;

public record GameSettingsResponse(
    String friendCode, boolean discoverable, boolean shareStreak, boolean sharePoints, boolean shareSteps) {
  public static GameSettingsResponse from(UserGameSettings settings) {
    return new GameSettingsResponse(settings.getFriendCode(), settings.isDiscoverable(),
        settings.isShareStreak(), settings.isSharePoints(), settings.isShareSteps());
  }
}
