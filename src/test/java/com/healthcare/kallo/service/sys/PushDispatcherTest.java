package com.healthcare.kallo.service.sys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import org.slf4j.MDC;
import com.healthcare.kallo.repository.sys.NotificationRepository;
import java.time.Clock;
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

    MDC.clear();
    new PushDispatcher(devices, sender, mock(NotificationRepository.class), Clock.systemUTC())
        .dispatch(userId, new PushMessage("SYSTEM", "id", "title", "body"));

    assertThat(MDC.get("request_id")).isNull();
    verify(devices).removeByToken("bad");
  }

  @Test
  void backgroundDispatchCreatesRequestIdForLogsWithoutChangingCallerMdc() {
    DeviceService devices = mock(DeviceService.class);
    PushSender sender = mock(PushSender.class);
    UUID userId = UUID.randomUUID();
    when(devices.tokensForUser(userId)).thenReturn(List.of());
    when(sender.send(any(), eq(List.of()))).thenReturn(List.of());
    MDC.clear();

    new PushDispatcher(devices, sender, mock(NotificationRepository.class), Clock.systemUTC())
        .dispatch(userId, new PushMessage("SYSTEM", "id", "title", "body"));

    assertThat(MDC.get("request_id")).isNull();
  }
}
