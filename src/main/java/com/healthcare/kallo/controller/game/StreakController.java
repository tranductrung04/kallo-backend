package com.healthcare.kallo.controller.game;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.response.game.StreakResponse;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.game.StreakService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/streak")
public class StreakController {
  private final StreakService streaks;

  public StreakController(StreakService streaks) {
    this.streaks = streaks;
  }

  @GetMapping
  public ApiResponse<StreakResponse> get(Authentication authentication) {
    return ApiResponse.success(streaks.get(CurrentUser.id(authentication)));
  }
}
