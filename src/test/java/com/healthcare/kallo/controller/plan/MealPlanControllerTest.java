package com.healthcare.kallo.controller.plan;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.common.PageResponse;
import com.healthcare.kallo.config.RateLimitTestConfig;
import com.healthcare.kallo.dto.response.plan.MealPlanDayResponse;
import com.healthcare.kallo.dto.response.plan.MealPlanItemResponse;
import com.healthcare.kallo.dto.response.plan.MealPlanResponse;
import com.healthcare.kallo.dto.response.plan.MealPlanSlotResponse;
import com.healthcare.kallo.dto.response.plan.MealPlanSummaryResponse;
import com.healthcare.kallo.model.food.enums.FoodSource;
import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.model.plan.enums.MealPlanStatus;
import com.healthcare.kallo.model.plan.enums.MealPlanType;
import com.healthcare.kallo.service.plan.MealPlanService;
import java.math.BigDecimal;
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

@WebMvcTest(value = MealPlanController.class, properties = "spring.profiles.active=test")
@Import(RateLimitTestConfig.class)
@AutoConfigureMockMvc
class MealPlanControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MealPlanService mealPlanService;

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
  void allMealPlanEndpointsReturnExpectedStatusesAndApiResponse() throws Exception {
    UUID planId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 11, 25);
    Instant now = Instant.parse("2026-11-25T05:00:00Z");

    MealPlanResponse sample =
        new MealPlanResponse(
            planId,
            "Thực đơn ngày 25/11/2026",
            null,
            MealPlanType.DAILY,
            date,
            1,
            MealPlanStatus.DRAFT,
            1850,
            1835,
            new BigDecimal("110.0"),
            new BigDecimal("200.0"),
            new BigDecimal("50.0"),
            new BigDecimal("20.0"),
            new BigDecimal("25.0"),
            130000,
            150000,
            new BigDecimal("0.82"),
            null,
            null,
            null,
            List.of(
                new MealPlanDayResponse(
                    1,
                    date,
                    1850,
                    1835,
                    new BigDecimal("-0.81"),
                    130000,
                    List.of(
                        new MealPlanSlotResponse(
                            MealType.BREAKFAST,
                            463,
                            450,
                            List.of(
                                new MealPlanItemResponse(
                                    itemId,
                                    UUID.randomUUID(),
                                    "Phở",
                                    null,
                                    FoodSource.SYSTEM,
                                    "1 tô phở bò",
                                    new BigDecimal("500.0"),
                                    new BigDecimal("1.00"),
                                    450,
                                    new BigDecimal("29.0"),
                                    new BigDecimal("56.0"),
                                    new BigDecimal("11.0"),
                                    new BigDecimal("4.0"),
                                    new BigDecimal("3.0"),
                                    50000)))))),
            now,
            now);

    when(mealPlanService.generate(eq(userId), any())).thenReturn(sample);
    when(mealPlanService.save(userId, planId)).thenReturn(sample);
    when(mealPlanService.getById(userId, planId)).thenReturn(sample);
    when(mealPlanService.swapItem(eq(userId), eq(planId), eq(itemId), any())).thenReturn(sample);
    when(mealPlanService.apply(userId, planId, date, null)).thenReturn(sample);
    when(mealPlanService.list(userId, 0, 20))
        .thenReturn(
            new PageResponse<>(
                List.of(
                    new MealPlanSummaryResponse(
                        planId,
                        sample.name(),
                        MealPlanType.DAILY,
                        date,
                        1,
                        MealPlanStatus.SAVED,
                        1850,
                        1835,
                        130000,
                        150000,
                        new BigDecimal("0.82"),
                        null,
                        now)),
                0,
                20,
                1,
                1));

    mockMvc
        .perform(
            post("/api/meal-plans/generate")
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planDate\":\"2026-11-25\",\"days\":1,\"maxBudgetVnd\":150000}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").value(planId.toString()))
        .andExpect(jsonPath("$.data.preferenceMatchScore").value(0.82));

    mockMvc
        .perform(post("/api/meal-plans/{id}/save", planId).with(auth()))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/meal-plans").with(auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].id").value(planId.toString()));

    mockMvc
        .perform(get("/api/meal-plans/{id}", planId).with(auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.days[0].dayIndex").value(1));

    mockMvc
        .perform(post("/api/meal-plans/{id}/items/{itemId}/swap", planId, itemId).with(auth()))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/meal-plans/{id}/apply", planId).param("date", "2026-11-25").with(auth()))
        .andExpect(status().isOk());

    mockMvc
        .perform(delete("/api/meal-plans/{id}", planId).with(auth()))
        .andExpect(status().isNoContent());
    verify(mealPlanService).delete(userId, planId);
  }
}
