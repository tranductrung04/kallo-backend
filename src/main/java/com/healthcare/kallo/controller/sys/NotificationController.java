package com.healthcare.kallo.controller.sys;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.response.sys.NotificationPageResponse;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.sys.NotificationQuery;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
  private final NotificationQuery notifications;

  public NotificationController(NotificationQuery notifications) {
    this.notifications = notifications;
  }

  @GetMapping
  public ApiResponse<NotificationPageResponse> list(
      Authentication authentication,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(defaultValue = "false") boolean unreadOnly) {
    return ApiResponse.success(
        notifications.page(CurrentUser.id(authentication), cursor, limit, unreadOnly));
  }

  @GetMapping("/unread-count")
  public ApiResponse<Map<String, Long>> unreadCount(Authentication authentication) {
    return ApiResponse.success(Map.of("count", notifications.unreadCount(CurrentUser.id(authentication))));
  }

  @PutMapping("/{id}/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void markRead(Authentication authentication, @PathVariable UUID id) {
    notifications.markRead(CurrentUser.id(authentication), id);
  }

  @PutMapping("/read-all")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void markAllRead(Authentication authentication) {
    notifications.markAllRead(CurrentUser.id(authentication));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(Authentication authentication, @PathVariable UUID id) {
    notifications.delete(CurrentUser.id(authentication), id);
  }
}
