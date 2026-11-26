package com.healthcare.kallo.controller.nutri;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.config.RateLimitTestConfig;
import com.healthcare.kallo.dto.response.nutri.DailyWaterLogsResponse;
import com.healthcare.kallo.dto.response.nutri.WaterLogResponse;
import com.healthcare.kallo.service.nutri.WaterLogService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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
  void logGetAndDeleteWaterEndpointsWork() throws Exception {
    UUID logId = UUID.randomUUID();
    UUID clientUuid = UUID.randomUUID();
    Instant now = Instant.parse("2026-11-25T05:00:00Z");
    LocalDate today = LocalDate.of(2026, 11, 25);
    WaterLogResponse sample =
        new WaterLogResponse(logId, 300, today, now, "Glass", clientUuid, now, now);

    when(waterLogService.logWater(eq(userId), any()))
        .thenReturn(
            new WaterLogService.CreateResult(sample, true),
            new WaterLogService.CreateResult(sample, false));
    when(waterLogService.getByDate(userId, today))
        .thenReturn(new DailyWaterLogsResponse(today, 300, 2000, List.of(sample)));

    String body =
        """
        {
          "amountMl": 300,
          "clientUuid": "%s",
          "note": "Glass"
        }
        """
            .formatted(clientUuid);

    mockMvc
        .perform(
            post("/api/water-logs")
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.amountMl").value(300));

    mockMvc
        .perform(
            post("/api/water-logs")
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/water-logs").param("date", "2026-11-25").with(auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalWaterMl").value(300));

    mockMvc
        .perform(delete("/api/water-logs/{id}", logId).with(auth()))
        .andExpect(status().isNoContent());
    verify(waterLogService).delete(userId, logId);
  }
}
