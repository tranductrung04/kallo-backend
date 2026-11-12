package com.healthcare.kallo.service.sys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.Test;

class FcmPushSenderTest {
  @Test
  void retryableFailureIsAttemptedThreeTimes() throws Exception {
    FirebaseMessaging messaging = mock(FirebaseMessaging.class);
    FirebaseMessagingException failure = mock(FirebaseMessagingException.class);
    when(failure.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNAVAILABLE);
    when(messaging.send(any())).thenThrow(failure);

    PushResult result =
        new FcmPushSender(messaging)
            .send(new PushMessage("SYSTEM", "id", "title", "body"), java.util.List.of("token"))
            .getFirst();

    assertThat(result.status()).isEqualTo(PushResult.Status.RETRYABLE_FAILURE);
    verify(messaging, times(3)).send(any());
  }

  @Test
  void invalidArgumentDoesNotDeleteTokenAsIfItWereUnregistered() throws Exception {
    FirebaseMessaging messaging = mock(FirebaseMessaging.class);
    FirebaseMessagingException failure = mock(FirebaseMessagingException.class);
    when(failure.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INVALID_ARGUMENT);
    when(messaging.send(any())).thenThrow(failure);

    PushResult result =
        new FcmPushSender(messaging)
            .send(new PushMessage("SYSTEM", "id", "title", "body"), java.util.List.of("token"))
            .getFirst();

    assertThat(result.status()).isEqualTo(PushResult.Status.FAILURE);
    verify(messaging).send(any());
  }
}
