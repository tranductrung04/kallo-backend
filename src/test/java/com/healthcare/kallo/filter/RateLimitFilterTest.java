package com.healthcare.kallo.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.healthcare.kallo.config.RateLimitProperties;
import com.healthcare.kallo.service.auth.RateLimiter;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class RateLimitFilterTest {
  private final RateLimitProperties properties = new RateLimitProperties(20, 30, 60, 120, 30, 10);

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void thirtyFirstSyncPushReturns429WithRetryAfter() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    RateLimiter limiter = (key, limit, window) -> {
      int count = calls.incrementAndGet();
      return count <= 30 ? RateLimiter.Result.permit() : RateLimiter.Result.rejected(17);
    };
    RateLimitFilter filter = new RateLimitFilter(limiter, properties, new tools.jackson.databind.ObjectMapper());
    SecurityContextHolder.getContext().setAuthentication(authentication());

    for (int i = 1; i <= 31; i++) {
      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/sync/push");
      request.setRequestURI("/api/sync/push");
      request.setServletPath("/api/sync/push");
      MockHttpServletResponse response = new MockHttpServletResponse();
      var chain = mock(jakarta.servlet.FilterChain.class);
      filter.doFilter(request, response, chain);
      if (i == 1) assertThat(calls.get()).isEqualTo(1);
      if (i == 31) {
        assertThat(calls.get()).isEqualTo(31);
        assertThat(response.getStatus()).as("status=%s", response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("17");
        verifyNoInteractions(chain);
      } else {
        verify(chain).doFilter(request, response);
      }
    }
  }

  @Test
  void limiterFailureFailsOpen() throws Exception {
    RateLimiter limiter = (key, limit, window) -> { throw new IllegalStateException("redis down"); };
    RateLimitFilter filter = new RateLimitFilter(limiter, properties, new tools.jackson.databind.ObjectMapper());
    SecurityContextHolder.getContext().setAuthentication(authentication());
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/sync/push");
    request.setServletPath("/api/sync/push");
    MockHttpServletResponse response = new MockHttpServletResponse();
    var chain = mock(jakarta.servlet.FilterChain.class);

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(200);
  }

  private UsernamePasswordAuthenticationToken authentication() {
    Jwt jwt = Jwt.withTokenValue("token").subject(java.util.UUID.randomUUID().toString())
        .header("alg", "none").build();
    return new UsernamePasswordAuthenticationToken(jwt, "token", java.util.List.of());
  }

}
