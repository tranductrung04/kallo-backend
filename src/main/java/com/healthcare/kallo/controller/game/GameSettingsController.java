package com.healthcare.kallo.controller.game;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.request.game.UpdateGameSettingsRequest;
import com.healthcare.kallo.dto.response.game.GameSettingsResponse;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.game.GameSettingsService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me/game-settings")
public class GameSettingsController {
  private final GameSettingsService settings;

  public GameSettingsController(GameSettingsService settings) { this.settings = settings; }

  @GetMapping
  public ApiResponse<GameSettingsResponse> get(Authentication authentication) {
    return ApiResponse.success(GameSettingsResponse.from(settings.getOrCreate(CurrentUser.id(authentication))));
  }

  @PutMapping
  public ApiResponse<GameSettingsResponse> update(@Valid @RequestBody UpdateGameSettingsRequest request,
      Authentication authentication) {
    return ApiResponse.success(GameSettingsResponse.from(settings.update(CurrentUser.id(authentication), request)));
  }
}
