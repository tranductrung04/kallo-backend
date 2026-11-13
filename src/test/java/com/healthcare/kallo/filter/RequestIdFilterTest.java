package com.healthcare.kallo.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {
  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void invalidClientRequestIdIsReplaced() throws Exception {
    RequestIdFilter filter = new RequestIdFilter();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Request-Id", "bad\nvalue");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, mock(FilterChain.class));

    String id = response.getHeader("X-Request-Id");
    assertThat(id).matches("[A-Za-z0-9._-]{1,128}");
    assertThat(id).doesNotContain("bad");
  }

  @Test
  void validRequestIdIsReturned() throws Exception {
    RequestIdFilter filter = new RequestIdFilter();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Request-Id", "req-123");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, mock(FilterChain.class));

    assertThat(response.getHeader("X-Request-Id")).isEqualTo("req-123");
  }
}
