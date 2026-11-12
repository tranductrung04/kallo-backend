package com.healthcare.kallo.service.sys;

import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PushDispatcherTest {
  @Test
  void invalidTokensAreRemovedThroughDeviceService() {
    DeviceService devices = mock(DeviceService.class);
    PushSender sender = mock(PushSender.class);
    UUID userId = UUID.randomUUID();
    when(devices.tokensForUser(userId)).thenReturn(List.of("bad"));
    when(sender.send(any(), eq(List.of("bad"))))
        .thenReturn(List.of(new PushResult("bad", PushResult.Status.INVALID_TOKEN)));

    new PushDispatcher(devices, sender)
        .dispatch(userId, new PushMessage("SYSTEM", "id", "title", "body"));

    verify(devices).removeByToken("bad");
  }
}
