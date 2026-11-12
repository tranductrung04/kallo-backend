package com.healthcare.kallo.controller.sys;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.config.AttributionProperties;
import com.healthcare.kallo.config.GoogleProperties;
import com.healthcare.kallo.config.JwtProperties;
import com.healthcare.kallo.config.SecurityConfig;
import com.healthcare.kallo.controller.sys.AttributionController;
import com.healthcare.kallo.security.auth.CustomAccessDeniedHandler;
import com.healthcare.kallo.security.auth.CustomAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AttributionController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, AttributionSecurityTest.TestBeans.class})
class AttributionSecurityTest {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private CustomAuthenticationEntryPoint authenticationEntryPoint;
  @MockitoBean private CustomAccessDeniedHandler accessDeniedHandler;

  @Test
  void publicAttributionDoesNotRequireToken() throws Exception {
    mockMvc.perform(get("/api/app/attributions")).andExpect(status().isOk());
  }

  @Test
  void expiredBearerTokenIsIgnoredOnPublicPath() throws Exception {
    mockMvc
        .perform(
            get("/api/app/attributions")
                .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.expired.signature"))
        .andExpect(status().isOk());
  }

  @TestConfiguration
  static class TestBeans {
    @Bean
    AttributionProperties attributionProperties() {
      return new AttributionProperties(
          "https://world.openfoodfacts.org",
          "ODbL-1.0",
          "Data from Open Food Facts.",
          "https://viendinhduong.vn/",
          "Vietnamese Food Composition Table terms",
          "Dá»¯ liá»‡u tá»« Viá»‡n Dinh dÆ°á»¡ng Quá»‘c gia.",
          "https://www.fao.org/infoods",
          "FAO/INFOODS terms of use",
          "Dá»¯ liá»‡u tá»« FAO/INFOODS.");
    }

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
