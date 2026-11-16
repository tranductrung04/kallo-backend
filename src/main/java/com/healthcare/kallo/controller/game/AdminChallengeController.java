package com.healthcare.kallo.controller.game;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.request.game.CreateChallengeRequest;
import com.healthcare.kallo.dto.request.game.UpdateChallengeRequest;
import com.healthcare.kallo.dto.response.game.AdminChallengeResponse;
import com.healthcare.kallo.service.game.AdminChallengeService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/challenges")
public class AdminChallengeController {
  private final AdminChallengeService challenges;

  public AdminChallengeController(AdminChallengeService challenges) {
    this.challenges = challenges;
  }

  @GetMapping
  public ApiResponse<List<AdminChallengeResponse>> list() {
    return ApiResponse.success(challenges.list());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<AdminChallengeResponse> create(@Valid @RequestBody CreateChallengeRequest request) {
    return ApiResponse.success(challenges.create(request));
  }

  @PutMapping("/{id}")
  public ApiResponse<AdminChallengeResponse> update(@PathVariable UUID id,
      @Valid @RequestBody UpdateChallengeRequest request) {
    return ApiResponse.success(challenges.update(id, request));
  }
}
