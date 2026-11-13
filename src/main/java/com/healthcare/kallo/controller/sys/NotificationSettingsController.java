package com.healthcare.kallo.controller.sys;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.request.sys.NotificationSettingsRequest;
import com.healthcare.kallo.dto.response.sys.NotificationSettingsResponse;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.sys.NotificationSettingsService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notification-settings")
public class NotificationSettingsController {
  private final NotificationSettingsService settings;

  public NotificationSettingsController(NotificationSettingsService settings) {
    this.settings = settings;
  }

  @GetMapping
  public ApiResponse<NotificationSettingsResponse> get(Authentication authentication) {
    return ApiResponse.success(NotificationSettingsResponse.from(
        settings.getOrCreate(CurrentUser.id(authentication))));
  }

  @PutMapping
  public ApiResponse<NotificationSettingsResponse> update(
      Authentication authentication, @Valid @RequestBody NotificationSettingsRequest request) {
    return ApiResponse.success(NotificationSettingsResponse.from(
        settings.update(CurrentUser.id(authentication), request)));
  }
}
