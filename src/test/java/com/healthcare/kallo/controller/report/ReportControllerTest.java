package com.healthcare.kallo.controller.report;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.config.RateLimitTestConfig;
import com.healthcare.kallo.dto.response.report.CaloriesReportResponse;
import com.healthcare.kallo.dto.response.report.DashboardReportResponse;
import com.healthcare.kallo.dto.response.report.WeightForecastResponse;
import com.healthcare.kallo.service.report.ReportService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

@WebMvcTest(value = ReportController.class, properties = "spring.profiles.active=test")
@Import(RateLimitTestConfig.class)
@AutoConfigureMockMvc
class ReportControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private ReportService reportService;

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
  @DisplayName("GET /api/reports/dashboard returns 200 OK and dashboard data")
  void getDashboard_returnsSuccess() throws Exception {
    UUID userId = UUID.randomUUID();
    LocalDate today = LocalDate.of(2026, 11, 23);

    DashboardReportResponse response = new DashboardReportResponse(
        today,
        new DashboardReportResponse.CalorieStats(1800, 2000, 300, 1500, 200, "DEFICIT", 90.0),
        new DashboardReportResponse.MacroStats(
            new DashboardReportResponse.NutrientProgress(BigDecimal.valueOf(110), BigDecimal.valueOf(120), 91.7),
            new DashboardReportResponse.NutrientProgress(BigDecimal.valueOf(200), BigDecimal.valueOf(250), 80.0),
            new DashboardReportResponse.NutrientProgress(BigDecimal.valueOf(50), BigDecimal.valueOf(60), 83.3),
            new DashboardReportResponse.SugarProgress(BigDecimal.valueOf(15), false),
            new DashboardReportResponse.FiberProgress(BigDecimal.valueOf(20))
        ),
        new DashboardReportResponse.WaterStats(2000, 2500, 80.0),
        new DashboardReportResponse.ActivityStats(7500, 300, BigDecimal.valueOf(68.5)),
        new DashboardReportResponse.GoalStats(true, false, false, 3)
    );

    when(reportService.getDashboard(eq(userId), any())).thenReturn(response);

    mockMvc.perform(get("/api/reports/dashboard")
            .with(userJwt(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.calories.consumed").value(1800))
        .andExpect(jsonPath("$.data.water.consumedMl").value(2000));
  }

  @Test
  @DisplayName("GET /api/reports/weight-forecast returns 200 OK and forecast data")
  void getWeightForecast_returnsSuccess() throws Exception {
    UUID userId = UUID.randomUUID();

    WeightForecastResponse response = new WeightForecastResponse(
        "SUFFICIENT_DATA", 14, 14, BigDecimal.valueOf(70.0), BigDecimal.valueOf(65.0),
        2200, -500, BigDecimal.valueOf(-0.45), "ON_TRACK",
        LocalDate.of(2027, 2, 1), 70, List.of(), "Rất tốt"
    );

    when(reportService.getWeightForecast(eq(userId))).thenReturn(response);

    mockMvc.perform(get("/api/reports/weight-forecast")
            .with(userJwt(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("SUFFICIENT_DATA"))
        .andExpect(jsonPath("$.data.trend").value("ON_TRACK"));
  }
}
