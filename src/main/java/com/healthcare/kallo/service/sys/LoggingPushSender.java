package com.healthcare.kallo.service.sys;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.push.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingPushSender implements PushSender {
  private static final Logger log = LoggerFactory.getLogger(LoggingPushSender.class);

  @Override
  public List<PushResult> send(PushMessage message, List<String> tokens) {
    return tokens.stream().map(token -> {
      log.info("Push {} sent to device ending {}", message.type(), suffix(token));
      return new PushResult(token, PushResult.Status.SENT);
    }).toList();
  }

  private static String suffix(String token) {
    return token.length() <= 6 ? token : token.substring(token.length() - 6);
  }
}
