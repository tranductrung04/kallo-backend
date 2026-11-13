package com.healthcare.kallo.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

class AsyncConfigTest {
  private final TaskDecorator decorator = new AsyncConfig().mdcTaskDecorator();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void propagatesRequestIdAndRestoresWorkerContext() {
    MDC.put("request_id", "req-123");
    AtomicReference<String> observed = new AtomicReference<>();
    decorator.decorate(() -> observed.set(MDC.get("request_id"))).run();

    assertThat(observed).hasValue("req-123");
    assertThat(MDC.get("request_id")).isEqualTo("req-123");
  }

  @Test
  void createsAsyncRequestIdWhenCallerHasNoRequestContext() {
    MDC.clear();
    AtomicReference<String> observed = new AtomicReference<>();
    decorator.decorate(() -> observed.set(MDC.get("request_id"))).run();

    assertThat(observed).hasValueSatisfying(value -> assertThat(value).startsWith("async-"));
    assertThat(MDC.get("request_id")).isNull();
  }
}
