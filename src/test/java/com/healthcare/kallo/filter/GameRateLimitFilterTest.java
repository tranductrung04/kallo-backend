package com.healthcare.kallo.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.healthcare.kallo.config.GameProperties;
import com.healthcare.kallo.config.RateLimitProperties;
import com.healthcare.kallo.service.auth.RateLimiter;
import static com.healthcare.kallo.config.GamePropertiesFixture.defaults;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.ObjectMapper;

class GameRateLimitFilterTest {
  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void searchUsesThirtyRequestsPerMinuteByDefault() throws Exception {
    RateLimiter limiter = mock(RateLimiter.class);
    when(limiter.check(anyString(), anyInt(), any())).thenReturn(RateLimiter.Result.permit());
    RateLimitFilter filter = new RateLimitFilter(limiter,
        new RateLimitProperties(20, 30, 60, 120, 30, 10, 30), gameProperties(), new ObjectMapper());
    SecurityContextHolder.getContext().setAuthentication(authentication());
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/search");
    request.setServletPath("/api/users/search");

    filter.doFilter(request, new MockHttpServletResponse(), mock(jakarta.servlet.FilterChain.class));

    verify(limiter).check(startsWith("user-search:"), eq(30), eq(Duration.ofMinutes(1)));
  }

  @Test
  void rejectedSearchReturns429WithRetryAfter() throws Exception {
    RateLimiter limiter = mock(RateLimiter.class);
    when(limiter.check(anyString(), eq(30), eq(Duration.ofMinutes(1))))
        .thenReturn(RateLimiter.Result.rejected(23));
    RateLimitFilter filter = new RateLimitFilter(limiter,
        new RateLimitProperties(20, 30, 60, 120, 30, 10, 30), gameProperties(), new ObjectMapper());
    SecurityContextHolder.getContext().setAuthentication(authentication());
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/search");
    request.setServletPath("/api/users/search");
    MockHttpServletResponse response = new MockHttpServletResponse();
    var chain = mock(jakarta.servlet.FilterChain.class);

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(429);
    assertThat(response.getHeader("Retry-After")).isEqualTo("23");
    verifyNoInteractions(chain);
  }

  @Test
  void searchLimiterFailureFailsOpen() throws Exception {
    RateLimiter limiter = (key, limit, window) -> { throw new IllegalStateException("redis down"); };
    RateLimitFilter filter = new RateLimitFilter(limiter,
        new RateLimitProperties(20, 30, 60, 120, 30, 10, 30), gameProperties(), new ObjectMapper());
    SecurityContextHolder.getContext().setAuthentication(authentication());
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/search");
    request.setServletPath("/api/users/search");
    MockHttpServletResponse response = new MockHttpServletResponse();
    var chain = mock(jakarta.servlet.FilterChain.class);

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  private GameProperties gameProperties() {
    return defaults();
  }

  private UsernamePasswordAuthenticationToken authentication() {
    Jwt jwt = Jwt.withTokenValue("token").subject(UUID.randomUUID().toString())
        .header("alg", "none").build();
    return new UsernamePasswordAuthenticationToken(jwt, "token", List.of());
  }
}
