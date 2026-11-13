package com.healthcare.kallo.controller.act;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.request.act.HealthSyncRequest;
import com.healthcare.kallo.dto.response.act.HealthSyncResponse;
import com.healthcare.kallo.dto.response.act.HealthSyncStatusResponse;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.act.HealthSyncService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/health-sync")
public class HealthSyncController {
  private final HealthSyncService healthSync;

  public HealthSyncController(HealthSyncService healthSync) {
    this.healthSync = healthSync;
  }

  @PostMapping
  public ApiResponse<HealthSyncResponse> sync(
      @Valid @RequestBody HealthSyncRequest request, Authentication authentication) {
    return ApiResponse.success(healthSync.sync(CurrentUser.id(authentication), request));
  }

  @GetMapping("/status")
  public ApiResponse<List<HealthSyncStatusResponse>> status(Authentication authentication) {
    return ApiResponse.success(healthSync.status(CurrentUser.id(authentication)));
  }
}
