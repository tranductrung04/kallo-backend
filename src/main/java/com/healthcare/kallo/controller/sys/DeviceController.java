package com.healthcare.kallo.controller.sys;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.request.sys.RegisterDeviceRequest;
import com.healthcare.kallo.dto.response.sys.DeviceResponse;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.sys.DeviceService;
import com.healthcare.kallo.service.sys.PushDispatcher;
import com.healthcare.kallo.service.sys.PushMessage;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {
  private final DeviceService devices;
  private final PushDispatcher pushes;

  public DeviceController(DeviceService devices, PushDispatcher pushes) {
    this.devices = devices;
    this.pushes = pushes;
  }

  @PostMapping
  public ApiResponse<DeviceResponse> register(
      Authentication authentication, @Valid @RequestBody RegisterDeviceRequest request) {
    return ApiResponse.success(
        new DeviceResponse(devices.register(CurrentUser.id(authentication), request)));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unregister(Authentication authentication, @PathVariable UUID id) {
    devices.unregister(CurrentUser.id(authentication), id);
  }

}
