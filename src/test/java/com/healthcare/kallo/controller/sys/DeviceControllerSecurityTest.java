package com.healthcare.kallo.controller.sys;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;

import com.healthcare.kallo.config.GoogleProperties;
import com.healthcare.kallo.config.JwtProperties;
import com.healthcare.kallo.config.SecurityConfig;
import com.healthcare.kallo.controller.sys.DeviceController;
import com.healthcare.kallo.security.auth.CustomAccessDeniedHandler;
import com.healthcare.kallo.security.auth.CustomAuthenticationEntryPoint;
import com.healthcare.kallo.service.sys.DeviceService;
import com.healthcare.kallo.service.sys.PushDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = DeviceController.class, properties = "spring.profiles.active=test")
@AutoConfigureMockMvc
@Import({SecurityConfig.class, DeviceController.class, DeviceControllerSecurityTest.TestBeans.class})
class DeviceControllerSecurityTest {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private DeviceService devices;
  @MockitoBean private PushDispatcher pushes;
  @MockitoBean private CustomAuthenticationEntryPoint entryPoint;
  @MockitoBean private CustomAccessDeniedHandler deniedHandler;

  @BeforeEach
  void configureSecurityHandlers() throws Exception {
    org.mockito.Mockito.doAnswer(
            invocation -> {
              invocation.<jakarta.servlet.http.HttpServletResponse>getArgument(1)
                  .setStatus(401);
              return null;
            })
        .when(entryPoint)
        .commence(any(), any(), any());
    org.mockito.Mockito.doAnswer(
            invocation -> {
              invocation.<jakarta.servlet.http.HttpServletResponse>getArgument(1)
                  .setStatus(403);
              return null;
            })
        .when(deniedHandler)
        .handle(any(), any(), any());
  }

  @Test
  void missingBearerTokenReturns401() throws Exception {
    mockMvc.perform(post("/api/devices")).andExpect(status().isUnauthorized());
  }

  @Test
  void unverifiedUserReturns403() throws Exception {
    mockMvc
        .perform(post("/api/devices").with(jwt().jwt(jwt -> jwt.claim("email_verified", false))))
        .andExpect(status().isForbidden());
  }

  @TestConfiguration
  static class TestBeans {
    @Bean
    JwtProperties jwtProperties() {
      return new JwtProperties("test-secret-with-at-least-32-bytes-for-tests", 900000, 604800000, 3000);
    }

    @Bean
    GoogleProperties googleProperties() {
      return new GoogleProperties("test-google-client-id", "https://example.test/jwks");
    }
  }
}
