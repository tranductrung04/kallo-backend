package com.healthcare.kallo.controller.report;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.config.RateLimitTestConfig;
import com.healthcare.kallo.dto.response.report.DailySummaryResponse;
import com.healthcare.kallo.service.report.DailySummaryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
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

  private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor userJwt(UUID userId) {
    return SecurityMockMvcRequestPostProcessors.jwt()
        .jwt(
            Jwt.withTokenValue("token")
                .subject(userId.toString())
                .claim("email_verified", true)
                .header("alg", "none")
                .build());
  }

  @Test
  @DisplayName("GET /api/daily-summaries/today returns today summary")
  void getTodaySummary_returnsOk() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID summaryId = UUID.randomUUID();
    LocalDate today = LocalDate.of(2026, 11, 20);

    DailySummaryResponse response = new DailySummaryResponse(
        summaryId, today, 1750, 2000, 250, new BigDecimal("110.0"), new BigDecimal("190.0"),
        new BigDecimal("50.0"), new BigDecimal("20.0"), new BigDecimal("25.0"), 1800, 2000,
        450, 8000, new BigDecimal("68.0"), true, false, false, 3
    );

    when(dailySummaryService.getTodaySummary(userId)).thenReturn(response);

    mockMvc.perform(get("/api/daily-summaries/today")
            .with(userJwt(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.caloriesConsumed").value(1750))
        .andExpect(jsonPath("$.data.targetCalories").value(2000))
        .andExpect(jsonPath("$.data.goalAchieved").value(true));
  }

  @Test
  @DisplayName("GET /api/daily-summaries?date=2026-11-20 returns summary for date")
  void getSummary_returnsOk() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID summaryId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 11, 20);

    DailySummaryResponse response = new DailySummaryResponse(
        summaryId, date, 1500, 2000, 500, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 1000, 2000,
        0, 0, null, false, false, false, 2
    );

    when(dailySummaryService.getSummary(eq(userId), eq(date))).thenReturn(response);

    mockMvc.perform(get("/api/daily-summaries")
            .param("date", "2026-11-20")
            .with(userJwt(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.caloriesConsumed").value(1500));
  }

  @Test
  @DisplayName("POST /api/daily-summaries/recalculate forces recalculation")
  void recalculate_returnsOk() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID summaryId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 11, 20);

    DailySummaryResponse response = new DailySummaryResponse(
        summaryId, date, 1600, 2000, 400, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 1500, 2000,
        0, 0, null, false, false, false, 2
    );

    when(dailySummaryService.getSummary(eq(userId), eq(date))).thenReturn(response);

    mockMvc.perform(post("/api/daily-summaries/recalculate")
            .param("date", "2026-11-20")
            .with(userJwt(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.caloriesConsumed").value(1600));
  }
}
