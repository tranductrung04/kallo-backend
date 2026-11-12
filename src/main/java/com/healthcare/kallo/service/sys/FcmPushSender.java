package com.healthcare.kallo.service.sys;

import com.google.firebase.messaging.*;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.push.enabled", havingValue = "true")
public class FcmPushSender implements PushSender {
  private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);
  private final FirebaseMessaging messaging;

  public FcmPushSender(FirebaseMessaging messaging) {
    this.messaging = messaging;
  }

  @Override
  public List<PushResult> send(PushMessage message, List<String> tokens) {
    List<PushResult> results = new ArrayList<>();
    for (String token : tokens) results.add(sendOne(message, token));
    return results;
  }

  private PushResult sendOne(PushMessage push, String token) {
    Message message =
        Message.builder()
            .setToken(token)
            .setNotification(
                Notification.builder().setTitle(push.title()).setBody(push.body()).build())
            .putAllData(push.data())
            .setAndroidConfig(
                AndroidConfig.builder().setTtl(600_000L).build())
            .setApnsConfig(
                ApnsConfig.builder()
                    .putHeader("apns-expiration", "600")
                    .setAps(Aps.builder().build())
                    .build())
            .build();
    for (int attempt = 1; attempt <= 3; attempt++) {
      try {
        messaging.send(message);
        return new PushResult(token, PushResult.Status.SENT);
      } catch (FirebaseMessagingException e) {
        if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
          return new PushResult(token, PushResult.Status.INVALID_TOKEN);
        }
        if (!isRetryable(e) || attempt == 3) {
          if (!isRetryable(e)) {
            log.error("Non-retryable FCM push failure after {} attempt(s)", attempt, e);
            return new PushResult(token, PushResult.Status.FAILURE);
          }
          log.warn("Push failed after {} attempt(s): {}", attempt, e.getMessage());
          return new PushResult(token, PushResult.Status.RETRYABLE_FAILURE);
        }
        try {
          Thread.sleep(100L * (1L << (attempt - 1)));
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          return new PushResult(token, PushResult.Status.RETRYABLE_FAILURE);
        }
      }
    }
    return new PushResult(token, PushResult.Status.RETRYABLE_FAILURE);
  }

  private boolean isRetryable(FirebaseMessagingException e) {
    return e.getMessagingErrorCode() == MessagingErrorCode.UNAVAILABLE
        || e.getMessagingErrorCode() == MessagingErrorCode.INTERNAL
        || e.getMessagingErrorCode() == MessagingErrorCode.QUOTA_EXCEEDED;
  }
}
