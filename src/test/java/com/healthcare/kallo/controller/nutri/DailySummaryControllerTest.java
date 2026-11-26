package com.healthcare.kallo.controller.nutri;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.config.RateLimitTestConfig;
import com.healthcare.kallo.dto.response.nutri.DailySummaryResponse;
import com.healthcare.kallo.service.nutri.DailySummaryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

@WebMvcTest(value = DailySummaryController.class, properties = "spring.profiles.active=test")
@Import(RateLimitTestConfig.class)
@AutoConfigureMockMvc
class DailySummaryControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private DailySummaryService dailySummaryService;

  private final UUID userId = UUID.randomUUID();

  private org.springframework.test.web.servlet.request.RequestPostProcessor auth() {
    Jwt jwt =
        Jwt.withTokenValue("mock-token")
            .header("alg", "none")
            .subject(userId.toString())
            .claim("email_verified", true)
            .build();
    return SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt);
  }

  @Test
  void dailySummaryEndpointsReturnApiResponse() throws Exception {
    LocalDate date = LocalDate.of(2026, 11, 25);
    DailySummaryResponse summary =
        new DailySummaryResponse(
            date,
            1800,
            2000,
            200,
            new BigDecimal("90.0"),
            new BigDecimal("200.0"),
            new BigDecimal("50.0"),
            new BigDecimal("25.0"),
            new BigDecimal("22.0"),
            1500,
            2000,
            300,
            6000,
            new BigDecimal("65.0"),
            true,
            false,
            false,
            2);

    when(dailySummaryService.getToday(userId)).thenReturn(summary);
    when(dailySummaryService.getByDate(userId, date)).thenReturn(summary);
    when(dailySummaryService.getRange(userId, date.minusDays(1), date)).thenReturn(List.of(summary));
    when(dailySummaryService.recalculate(userId, date)).thenReturn(summary);

    mockMvc
        .perform(get("/api/daily-summaries/today").with(auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.caloriesConsumed").value(1800))
        .andExpect(jsonPath("$.data.goalAchieved").value(true));

    mockMvc
        .perform(get("/api/daily-summaries").param("date", "2026-11-25").with(auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.steps").value(6000));

    mockMvc
        .perform(
            get("/api/daily-summaries/range")
                .param("from", "2026-11-24")
                .param("to", "2026-11-25")
                .with(auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].mealsLoggedCount").value(2));

    mockMvc
        .perform(post("/api/daily-summaries/recalculate").param("date", "2026-11-25").with(auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.waterMl").value(1500));
  }
}
