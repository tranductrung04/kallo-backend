package com.healthcare.kallo.service.sys.impl;

import com.healthcare.kallo.config.NotificationProperties;
import com.healthcare.kallo.dto.request.sys.RegisterDeviceRequest;
import com.healthcare.kallo.model.sys.DeviceToken;
import com.healthcare.kallo.repository.sys.DeviceTokenRepository;
import com.healthcare.kallo.service.sys.DeviceService;
import java.time.Instant;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class DeviceServiceImpl implements DeviceService {
  private final DeviceTokenRepository devices;
  private final NotificationProperties properties;
  private final Clock clock;

  @Autowired
  public DeviceServiceImpl(
      DeviceTokenRepository devices, NotificationProperties properties, Clock clock) {
    this.devices = devices;
    this.properties = properties;
    this.clock = clock;
  }

  public DeviceServiceImpl(DeviceTokenRepository devices, NotificationProperties properties) {
    this(devices, properties, Clock.systemUTC());
  }

  @Override
  @Transactional
  public UUID register(UUID userId, RegisterDeviceRequest request) {
    String token = request.token().trim();
    Instant now = Instant.now(clock);
    devices.upsert(
        userId, token, request.platform().name(), request.locale(), request.appVersion(), now);

    List<DeviceToken> userDevices = devices.findByUserIdOrderByLastSeenAtAsc(userId);
    int excess = userDevices.size() - properties.maxDevicesPerUser();
    for (int i = 0; i < excess; i++) {
      devices.delete(userDevices.get(i));
    }
    return devices.findByToken(token).orElseThrow().getId();
  }

  @Override
  @Transactional
  public void unregister(UUID userId, UUID deviceId) {
    devices.deleteByIdAndUserId(deviceId, userId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<String> tokensForUser(UUID userId) {
    return devices.findByUserIdOrderByLastSeenAtAsc(userId).stream()
        .map(DeviceToken::getToken)
        .toList();
  }

  @Override
  @Transactional
  public void removeByToken(String token) {
    devices.deleteByToken(token);
  }
}
