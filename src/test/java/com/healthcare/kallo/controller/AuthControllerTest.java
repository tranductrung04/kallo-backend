package com.healthcare.kallo.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.dto.response.AuthResponse;
import com.healthcare.kallo.dto.response.UserSummary;
import com.healthcare.kallo.service.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = AuthController.class, properties = "spring.profiles.active=test")
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuthService authService;

  @Test
  void registerRejectsInvalidPasswordWithFieldDetails() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"not-an-email","password":"short","displayName":""}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.details.email").exists())
        .andExpect(jsonPath("$.error.details.password").exists());
  }

  @Test
  void passwordToStringIsRedacted() {
    com.healthcare.kallo.dto.request.LoginRequest request =
        new com.healthcare.kallo.dto.request.LoginRequest("user@example.com", "Password1");
    org.junit.jupiter.api.Assertions.assertFalse(request.toString().contains("Password1"));
  }

  @Test
  void registerReturnsCreatedAuthResponse() throws Exception {
    AuthResponse response =
        new AuthResponse(
            "access",
            "refresh",
            "Bearer",
            900,
            new UserSummary(UUID.randomUUID(), "user@example.com", "User", "USER"),
            false);
    when(authService.register(any())).thenReturn(response);

    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"user@example.com","password":"Password1","displayName":"User"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").value("access"))
        .andExpect(jsonPath("$.data.user.email").value("user@example.com"));
  }
}
