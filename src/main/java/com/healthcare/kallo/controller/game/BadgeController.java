package com.healthcare.kallo.controller.game;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.response.game.BadgeResponse;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.game.BadgeService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/badges")
public class BadgeController {
  private final BadgeService badges;

  public BadgeController(BadgeService badges) {
    this.badges = badges;
  }

  @GetMapping
  public ApiResponse<List<BadgeResponse>> list(Authentication authentication) {
    return ApiResponse.success(badges.list(CurrentUser.id(authentication)));
  }
}
