package com.healthcare.kallo.controller.game;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.response.game.LeaderboardResponse;
import com.healthcare.kallo.model.game.enums.LeaderboardMetric;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.game.LeaderboardService;
import java.util.Locale;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {
  private final LeaderboardService leaderboard;

  public LeaderboardController(LeaderboardService leaderboard) {
    this.leaderboard = leaderboard;
  }

  @GetMapping("/friends")
  public ApiResponse<LeaderboardResponse> friends(
      @RequestParam(defaultValue = "POINTS") String metric, Authentication authentication) {
    final LeaderboardMetric value;
    try {
      value = LeaderboardMetric.valueOf(metric.trim().toUpperCase(Locale.ROOT));
    } catch (RuntimeException exception) {
      throw new BusinessException(ErrorCode.INVALID_PARAMETER);
    }
    return ApiResponse.success(leaderboard.get(CurrentUser.id(authentication), value));
  }
}
