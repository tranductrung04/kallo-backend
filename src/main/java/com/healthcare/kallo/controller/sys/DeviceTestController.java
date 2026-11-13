package com.healthcare.kallo.controller.sys;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.sys.PushDispatcher;
import com.healthcare.kallo.service.sys.PushMessage;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("dev")
@RestController
@RequestMapping("/api/devices")
public class DeviceTestController {
  private final PushDispatcher pushes;

  public DeviceTestController(PushDispatcher pushes) {
    this.pushes = pushes;
  }

  @PostMapping("/test-push")
  public ApiResponse<Void> testPush(Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    pushes.dispatch(
        userId, new PushMessage("SYSTEM", "test", "Kallo", "Đây là thông báo thử nghiệm từ Kallo."));
    return ApiResponse.success(null);
  }
}
