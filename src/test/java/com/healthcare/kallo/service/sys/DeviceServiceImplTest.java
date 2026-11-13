package com.healthcare.kallo.service.sys;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.healthcare.kallo.config.NotificationProperties;
import com.healthcare.kallo.dto.request.sys.RegisterDeviceRequest;
import com.healthcare.kallo.model.sys.DeviceToken;
import com.healthcare.kallo.model.sys.enums.DevicePlatform;
import com.healthcare.kallo.repository.sys.DeviceTokenRepository;
import com.healthcare.kallo.service.sys.impl.DeviceServiceImpl;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceServiceImplTest {
  @Mock private DeviceTokenRepository repository;
  private DeviceServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new DeviceServiceImpl(
            repository, new NotificationProperties(30, 6, 2, 60, Duration.ofMinutes(10)));
  }

  @Test
  void registeringSameTokenUsesOneAtomicUpsert() {
    UUID userId = UUID.randomUUID();
    UUID deviceId = UUID.randomUUID();
    DeviceToken device = device(deviceId, userId, "token");
    when(repository.findByToken("token")).thenReturn(Optional.of(device));
    when(repository.findByUserIdOrderByLastSeenAtAsc(userId)).thenReturn(List.of(device));

    UUID first =
        service.register(
            userId, new RegisterDeviceRequest(" token ", DevicePlatform.ANDROID, "vi", "1"));
    UUID second =
        service.register(
            userId, new RegisterDeviceRequest("token", DevicePlatform.ANDROID, "vi", "1"));

    assert first.equals(deviceId);
    assert second.equals(deviceId);
    verify(repository, times(2))
        .upsert(eq(userId), eq("token"), eq("ANDROID"), eq("vi"), eq("1"), any(Instant.class));
    verify(repository, never()).save(any());
  }

  @Test
  void registeringTokenTransfersOwnership() {
    UUID newOwner = UUID.randomUUID();
    UUID deviceId = UUID.randomUUID();
    DeviceToken device = device(deviceId, UUID.randomUUID(), "token");
    when(repository.findByToken("token")).thenReturn(Optional.of(device));
    when(repository.findByUserIdOrderByLastSeenAtAsc(newOwner)).thenReturn(List.of(device));

    service.register(
        newOwner, new RegisterDeviceRequest("token", DevicePlatform.IOS, null, null));

    verify(repository)
        .upsert(eq(newOwner), eq("token"), eq("IOS"), isNull(), isNull(), any(Instant.class));
  }

  @Test
  void evictsOldestDevicesAboveConfiguredLimit() {
    UUID userId = UUID.randomUUID();
    DeviceToken oldest = device(UUID.randomUUID(), userId, "old");
    DeviceToken current = device(UUID.randomUUID(), userId, "current");
    DeviceToken newest = device(UUID.randomUUID(), userId, "new");
    when(repository.findByToken("new")).thenReturn(Optional.of(newest));
    when(repository.findByUserIdOrderByLastSeenAtAsc(userId))
        .thenReturn(List.of(oldest, current, newest));

    service.register(userId, new RegisterDeviceRequest("new", DevicePlatform.ANDROID, null, null));

    verify(repository).delete(oldest);
    verify(repository, never()).delete(current);
  }

  @Test
  void unregisterOnlyDeletesDeviceOwnedByCaller() {
    UUID userId = UUID.randomUUID();
    UUID deviceId = UUID.randomUUID();

    service.unregister(userId, deviceId);

    verify(repository).deleteByIdAndUserId(deviceId, userId);
  }

  private static DeviceToken device(UUID id, UUID userId, String token) {
    DeviceToken device = new DeviceToken();
    device.setId(id);
    device.setUserId(userId);
    device.setToken(token);
    device.setLastSeenAt(Instant.now());
    return device;
  }
}
