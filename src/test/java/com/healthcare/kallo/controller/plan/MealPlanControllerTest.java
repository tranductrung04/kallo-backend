package com.healthcare.kallo.controller.plan;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.config.RateLimitTestConfig;
import com.healthcare.kallo.dto.response.plan.ApplyMealPlanResponse;
import com.healthcare.kallo.dto.response.plan.MealPlanResponse;
import com.healthcare.kallo.dto.response.report.DailySummaryResponse;
import com.healthcare.kallo.model.plan.enums.MealPlanStatus;
import com.healthcare.kallo.model.plan.enums.MealPlanType;
import com.healthcare.kallo.service.plan.MealPlanService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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

@WebMvcTest(value = MealPlanController.class, properties = "spring.profiles.active=test")
@Import(RateLimitTestConfig.class)
@AutoConfigureMockMvc
class MealPlanControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private MealPlanService mealPlanService;

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
  @DisplayName("POST /api/meal-plans/generate returns 201 Created and meal plan response")
  void generateMealPlan_returnsCreated() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID planId = UUID.randomUUID();

    MealPlanResponse response = new MealPlanResponse(
        planId, "Thực đơn mẫu", "Mô tả", MealPlanStatus.DRAFT, MealPlanType.DAILY,
        LocalDate.of(2026, 11, 23), 2000, 1980, BigDecimal.valueOf(110),
        BigDecimal.valueOf(220), BigDecimal.valueOf(55), BigDecimal.valueOf(15),
        BigDecimal.valueOf(25), 120000, null, BigDecimal.valueOf(0.85), null,
        null, null, List.of()
    );

    when(mealPlanService.generateMealPlan(eq(userId), any())).thenReturn(response);

    mockMvc.perform(post("/api/meal-plans/generate")
            .with(userJwt(userId))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "planDate": "2026-11-23",
                  "days": 1
                }
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(planId.toString()))
        .andExpect(jsonPath("$.data.totalCalories").value(1980));
  }

  @Test
  @DisplayName("POST /api/meal-plans/{id}/apply returns 200 OK and apply response")
  void applyMealPlan_returnsSuccess() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID planId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 11, 23);

    DailySummaryResponse summary = new DailySummaryResponse(
        UUID.randomUUID(), date, 1980, 2000, 20, BigDecimal.valueOf(110), BigDecimal.valueOf(220),
        BigDecimal.valueOf(55), BigDecimal.valueOf(15), BigDecimal.valueOf(25),
        2000, 2000, 300, 5000, null, true, false, false, 4
    );

    ApplyMealPlanResponse response = new ApplyMealPlanResponse(
        planId, date, 4, 1980, summary
    );

    when(mealPlanService.applyMealPlan(eq(userId), eq(planId), eq(date))).thenReturn(response);

    mockMvc.perform(post("/api/meal-plans/{id}/apply", planId)
            .with(userJwt(userId))
            .param("date", "2026-11-23"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.mealPlanId").value(planId.toString()))
        .andExpect(jsonPath("$.data.logsCreatedCount").value(4));
  }

  @Test
  @DisplayName("DELETE /api/meal-plans/{id} returns 200 OK")
  void deleteMealPlan_returnsSuccess() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID planId = UUID.randomUUID();

    doNothing().when(mealPlanService).deleteMealPlan(eq(userId), eq(planId));

    mockMvc.perform(delete("/api/meal-plans/{id}", planId)
            .with(userJwt(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }
}
