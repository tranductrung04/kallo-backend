package com.healthcare.kallo.controller.game;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.config.RateLimitTestConfig;
import com.healthcare.kallo.dto.response.game.StreakResponse;
import com.healthcare.kallo.service.game.StreakService;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = StreakController.class, properties = "spring.profiles.active=test")
@Import(RateLimitTestConfig.class)
@AutoConfigureMockMvc
class StreakControllerTest {
  @Autowired MockMvc mockMvc;
  @MockitoBean StreakService streaks;

  @Test
  void returnsCurrentUserStreak() throws Exception {
    UUID userId = UUID.randomUUID();
    when(streaks.get(userId)).thenReturn(new StreakResponse(3, 5, LocalDate.of(2026, 11, 13), true, false));

    mockMvc.perform(get("/api/streak").with(
        SecurityMockMvcRequestPostProcessors.jwt().jwt(Jwt.withTokenValue("token")
            .subject(userId.toString()).header("alg", "none").build())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.currentStreak").value(3))
        .andExpect(jsonPath("$.data.longestStreak").value(5))
        .andExpect(jsonPath("$.data.todayCompleted").value(true));
  }

  @Test
  void rejectsUnauthenticatedRequest() throws Exception {
    mockMvc.perform(get("/api/streak")).andExpect(status().isUnauthorized());
  }
}
