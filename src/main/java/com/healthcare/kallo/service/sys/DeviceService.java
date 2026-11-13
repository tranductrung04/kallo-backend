package com.healthcare.kallo.service.sys;

import com.healthcare.kallo.dto.request.sys.RegisterDeviceRequest;
import com.healthcare.kallo.model.sys.DeviceToken;
import java.util.List;
import java.util.UUID;

public interface DeviceService {
  UUID register(UUID userId, RegisterDeviceRequest request);
  void unregister(UUID userId, UUID deviceId);
  List<String> tokensForUser(UUID userId);
  void removeByToken(String token);
}
