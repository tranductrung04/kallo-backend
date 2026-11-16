package com.healthcare.kallo.controller.game;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.response.game.ChallengeResponse;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.game.ChallengeService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/challenges")
public class ChallengeController {
  private final ChallengeService challenges;

  public ChallengeController(ChallengeService challenges) {
    this.challenges = challenges;
  }

  @GetMapping
  public ApiResponse<List<ChallengeResponse>> list(
      @RequestParam(required = false) String status, Authentication authentication) {
    return ApiResponse.success(challenges.list(CurrentUser.id(authentication), status));
  }

  @GetMapping("/{id}")
  public ApiResponse<ChallengeResponse> get(@PathVariable UUID id, Authentication authentication) {
    return ApiResponse.success(challenges.get(CurrentUser.id(authentication), id));
  }

  @PostMapping("/{id}/join")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<ChallengeResponse> join(@PathVariable UUID id, Authentication authentication) {
    return ApiResponse.success(challenges.join(CurrentUser.id(authentication), id));
  }
}
