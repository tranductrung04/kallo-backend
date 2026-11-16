package com.healthcare.kallo.controller.game;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.request.game.CreateBadgeRequest;
import com.healthcare.kallo.dto.request.game.UpdateBadgeRequest;
import com.healthcare.kallo.dto.response.game.AdminBadgeResponse;
import com.healthcare.kallo.service.game.AdminBadgeService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/badges")
public class AdminBadgeController {
  private final AdminBadgeService badges;

  public AdminBadgeController(AdminBadgeService badges) {
    this.badges = badges;
  }

  @GetMapping
  public ApiResponse<List<AdminBadgeResponse>> list() {
    return ApiResponse.success(badges.list());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<AdminBadgeResponse> create(@Valid @RequestBody CreateBadgeRequest request) {
    return ApiResponse.success(badges.create(request));
  }

  @PutMapping("/{id}")
  public ApiResponse<AdminBadgeResponse> update(@PathVariable UUID id,
      @Valid @RequestBody UpdateBadgeRequest request) {
    return ApiResponse.success(badges.update(id, request));
  }
}
