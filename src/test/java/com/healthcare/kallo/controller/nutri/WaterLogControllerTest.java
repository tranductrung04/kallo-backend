package com.healthcare.kallo.controller.nutri;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.config.RateLimitTestConfig;
import com.healthcare.kallo.dto.response.nutri.DailyWaterResponse;
import com.healthcare.kallo.dto.response.nutri.WaterLogResponse;
import com.healthcare.kallo.service.nutri.WaterLogService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = WaterLogController.class, properties = "spring.profiles.active=test")
@Import(RateLimitTestConfig.class)
@AutoConfigureMockMvc
class WaterLogControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private WaterLogService waterLogService;

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
  @DisplayName("POST /api/water-logs returns 201 Created")
  void createWaterLog_returnsCreated() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID logId = UUID.randomUUID();
    UUID clientUuid = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 11, 20);

    WaterLogResponse response = new WaterLogResponse(logId, 300, date, Instant.now(), clientUuid, "Glass 1");
    when(waterLogService.logWater(eq(userId), any())).thenReturn(response);

    String json = """
        {
          "amountMl": 300,
          "loggedDate": "2026-11-20",
          "clientUuid": "%s",
          "note": "Glass 1"
        }
        """.formatted(clientUuid);

    mockMvc.perform(post("/api/water-logs")
            .with(userJwt(userId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.amountMl").value(300));
  }

  @Test
  @DisplayName("GET /api/water-logs?date=2026-11-20 returns daily water total")
  void getDailyWater_returnsOk() throws Exception {
    UUID userId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 11, 20);

    DailyWaterResponse response = new DailyWaterResponse(date, 1500, 2000, Collections.emptyList());
    when(waterLogService.getDailyWater(eq(userId), eq(date))).thenReturn(response);

    mockMvc.perform(get("/api/water-logs")
            .param("date", "2026-11-20")
            .with(userJwt(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.totalWaterMl").value(1500))
        .andExpect(jsonPath("$.data.targetWaterMl").value(2000));
  }

  @Test
  @DisplayName("DELETE /api/water-logs/{id} returns 200 OK")
  void deleteWaterLog_returnsOk() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID logId = UUID.randomUUID();

    mockMvc.perform(delete("/api/water-logs/" + logId)
            .with(userJwt(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }
}
