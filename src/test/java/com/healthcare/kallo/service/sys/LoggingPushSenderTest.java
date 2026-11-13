package com.healthcare.kallo.service.sys;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LoggingPushSenderTest {
  @Test
  void logOnlyContainsTokenSuffix() {
    Logger logger = (Logger) LoggerFactory.getLogger(LoggingPushSender.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    String token = "token-value-that-must-not-be-logged";

    new LoggingPushSender()
        .send(new PushMessage("SYSTEM", "id", "title", "body"), java.util.List.of(token));

    String message = appender.list.getFirst().getFormattedMessage();
    assertThat(message).contains(token.substring(token.length() - 6));
    assertThat(message).doesNotContain(token.substring(0, token.length() - 6));
    logger.detachAppender(appender);
  }
}
