package com.healthcare.kallo.controller.nutri;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.config.RateLimitTestConfig;
import com.healthcare.kallo.dto.response.nutri.DailyFoodLogResponse;
import com.healthcare.kallo.dto.response.nutri.FoodLogResponse;
import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.model.nutri.enums.FoodLogSource;
import com.healthcare.kallo.service.nutri.FoodLogService;
import com.healthcare.kallo.service.nutri.FoodRecognitionService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

@WebMvcTest(value = FoodLogController.class, properties = "spring.profiles.active=test")
@Import(RateLimitTestConfig.class)
@AutoConfigureMockMvc
class FoodLogControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private FoodLogService foodLogService;
  @MockitoBean private FoodRecognitionService foodRecognitionService;

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
  @DisplayName("POST /api/food-logs returns 201 Created and log response")
  void createFoodLog_returnsCreated() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID logId = UUID.randomUUID();
    UUID foodId = UUID.randomUUID();
    UUID clientUuid = UUID.randomUUID();

    FoodLogResponse response = new FoodLogResponse(
        logId, foodId, "Phở bò", MealType.BREAKFAST, "1 tô", new BigDecimal("400.0"), BigDecimal.ONE,
        FoodLogSource.SEARCH, LocalDate.of(2026, 11, 20), Instant.now(), clientUuid, null,
        new BigDecimal("450.0"), new BigDecimal("30.0"), new BigDecimal("60.0"), new BigDecimal("10.0"),
        BigDecimal.ZERO, BigDecimal.ZERO, false, Collections.emptyList()
    );

    when(foodLogService.logFood(eq(userId), any())).thenReturn(response);

    String json = """
        {
          "foodId": "%s",
          "mealType": "BREAKFAST",
          "servingName": "1 tô",
          "servingGrams": 400.0,
          "servingQuantity": 1.0,
          "source": "SEARCH",
          "loggedDate": "2026-11-20",
          "clientUuid": "%s"
        }
        """.formatted(foodId, clientUuid);

    mockMvc.perform(post("/api/food-logs")
            .with(userJwt(userId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.foodName").value("Phở bò"))
        .andExpect(jsonPath("$.data.calories").value(450.0));
  }

  @Test
  @DisplayName("GET /api/food-logs?date=2026-11-20 returns daily food logs")
  void getDailyFoodLogs_returnsOk() throws Exception {
    UUID userId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 11, 20);

    DailyFoodLogResponse response = new DailyFoodLogResponse(
        date, 2000, new BigDecimal("450.0"), new BigDecimal("30.0"), new BigDecimal("60.0"),
        new BigDecimal("10.0"), BigDecimal.ZERO, BigDecimal.ZERO, Map.of(MealType.BREAKFAST, List.of())
    );

    when(foodLogService.getDailyFoodLogs(eq(userId), eq(date))).thenReturn(response);

    mockMvc.perform(get("/api/food-logs")
            .param("date", "2026-11-20")
            .with(userJwt(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.date").value("2026-11-20"))
        .andExpect(jsonPath("$.data.targetCalories").value(2000));
  }

  @Test
  @DisplayName("DELETE /api/food-logs/{id} returns 200 OK")
  void deleteFoodLog_returnsOk() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID logId = UUID.randomUUID();

    mockMvc.perform(delete("/api/food-logs/" + logId)
            .with(userJwt(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }
}
