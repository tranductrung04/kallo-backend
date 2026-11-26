package com.healthcare.kallo.controller.nutri;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.RateLimitTestConfig;
import com.healthcare.kallo.dto.response.food.FoodSearchItemResponse;
import com.healthcare.kallo.dto.response.nutri.CopyMealResponse;
import com.healthcare.kallo.dto.response.nutri.DailyFoodLogsResponse;
import com.healthcare.kallo.dto.response.nutri.FoodLogResponse;
import com.healthcare.kallo.dto.response.nutri.FoodRecognitionResponse;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.model.food.enums.FoodSource;
import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.model.nutri.enums.LogSource;
import com.healthcare.kallo.service.nutri.FoodLogService;
import com.healthcare.kallo.service.nutri.FoodRecognitionService;
import com.healthcare.kallo.service.nutri.NutriRateLimiter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
  @MockitoBean private NutriRateLimiter nutriRateLimiter;

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
  void logFoodReturns201WhenCreatedAnd200OnIdempotentReplay() throws Exception {
    UUID logId = UUID.randomUUID();
    UUID foodId = UUID.randomUUID();
    UUID clientUuid = UUID.randomUUID();
    FoodLogResponse sample = sampleFoodLog(logId, foodId, clientUuid);

    when(foodLogService.logFood(eq(userId), any()))
        .thenReturn(
            new FoodLogService.CreateResult(sample, true),
            new FoodLogService.CreateResult(sample, false));

    String payload =
        """
        {
          "foodId": "%s",
          "mealType": "LUNCH",
          "servingGrams": 150,
          "servingQuantity": 1.5,
          "clientUuid": "%s"
        }
        """
            .formatted(foodId, clientUuid);

    mockMvc
        .perform(
            post("/api/food-logs")
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(logId.toString()));

    mockMvc
        .perform(
            post("/api/food-logs")
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.clientUuid").value(clientUuid.toString()));
  }

  @Test
  void crudCopyRecentRecognizeAndFeedbackEndpointsWork() throws Exception {
    UUID logId = UUID.randomUUID();
    UUID foodId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 11, 25);
    FoodLogResponse sample = sampleFoodLog(logId, foodId, UUID.randomUUID());

    Map<MealType, List<FoodLogResponse>> grouped = new EnumMap<>(MealType.class);
    grouped.put(MealType.LUNCH, List.of(sample));
    when(foodLogService.getByDate(userId, date))
        .thenReturn(
            new DailyFoodLogsResponse(
                date,
                grouped,
                450,
                2000,
                1550,
                new BigDecimal("22.5"),
                new BigDecimal("56.3"),
                new BigDecimal("13.5"),
                new BigDecimal("4.5"),
                new BigDecimal("3.4"),
                1));
    when(foodLogService.getById(userId, logId)).thenReturn(sample);
    when(foodLogService.update(eq(userId), eq(logId), any())).thenReturn(sample);
    when(foodLogService.copyMeals(eq(userId), any()))
        .thenReturn(new CopyMealResponse(date.minusDays(1), date, 1, List.of(sample), List.of()));
    when(foodLogService.getRecentFoods(userId, 10))
        .thenReturn(
            List.of(
                new FoodSearchItemResponse(
                    foodId,
                    "Phở bò",
                    FoodCategory.NOODLE,
                    "1 tô",
                    new BigDecimal("350.0"),
                    450,
                    new BigDecimal("22.0"),
                    new BigDecimal("60.0"),
                    new BigDecimal("12.0"),
                    null,
                    FoodSource.SYSTEM,
                    true)));
    when(foodLogService.submitFeedback(eq(userId), eq(logId), any())).thenReturn(sample);

    mockMvc
        .perform(get("/api/food-logs").param("date", "2026-11-25").with(auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalCalories").value(450));

    mockMvc
        .perform(get("/api/food-logs/{id}", logId).with(auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.foodName").value("Phở bò"));

    mockMvc
        .perform(
            put("/api/food-logs/{id}", logId)
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mealType\":\"DINNER\",\"servingQuantity\":2}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(delete("/api/food-logs/{id}", logId).with(auth()))
        .andExpect(status().isNoContent());
    verify(foodLogService).delete(userId, logId);

    mockMvc
        .perform(
            post("/api/food-logs/copy")
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceDate\":\"2026-11-24\",\"targetDate\":\"2026-11-25\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.copiedCount").value(1));

    mockMvc
        .perform(get("/api/food-logs/recent").with(auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].name").value("Phở bò"));

    mockMvc
        .perform(
            post("/api/food-logs/{id}/feedback", logId)
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"consented\":true}"))
        .andExpect(status().isOk());

    MockMultipartFile photo =
        new MockMultipartFile(
            "image", "meal.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
    when(foodRecognitionService.recognize(eq(userId), any()))
        .thenReturn(
            new FoodRecognitionResponse(
                "temp/" + userId + "/x.jpg", "https://minio/x.jpg", "v1", List.of()));

    mockMvc
        .perform(multipart("/api/food-logs/recognize").file(photo).with(auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.modelVersion").value("v1"));
  }

  @Test
  void recognizeAlsoAcceptsFilePartNameFallback() throws Exception {
    MockMultipartFile photo =
        new MockMultipartFile(
            "file", "meal.png", "image/png", new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47});
    when(foodRecognitionService.recognize(eq(userId), any()))
        .thenReturn(
            new FoodRecognitionResponse(
                "temp/" + userId + "/x.png", "https://minio/x.png", "modal-food-scanner", List.of()));

    mockMvc
        .perform(multipart("/api/food-logs/recognize").file(photo).with(auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.modelVersion").value("modal-food-scanner"));
  }

  @Test
  void recognizeReturns429WhenRateLimited() throws Exception {
    doThrow(new BusinessException(ErrorCode.TOO_MANY_REQUESTS, 30L))
        .when(nutriRateLimiter)
        .checkRecognize(userId);
    MockMultipartFile photo =
        new MockMultipartFile("image", "meal.jpg", "image/jpeg", new byte[] {1, 2, 3});

    mockMvc
        .perform(multipart("/api/food-logs/recognize").file(photo).with(auth()))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error.code").value("TOO_MANY_REQUESTS"));
  }

  private FoodLogResponse sampleFoodLog(UUID id, UUID foodId, UUID clientUuid) {
    Instant now = Instant.parse("2026-11-25T05:00:00Z");
    return new FoodLogResponse(
        id,
        foodId,
        "Phở bò",
        MealType.LUNCH,
        "1 tô",
        new BigDecimal("350.0"),
        BigDecimal.ONE,
        LogSource.SEARCH,
        LocalDate.of(2026, 11, 25),
        now,
        null,
        new BigDecimal("450.0"),
        new BigDecimal("22.5"),
        new BigDecimal("56.3"),
        new BigDecimal("13.5"),
        new BigDecimal("4.5"),
        new BigDecimal("3.4"),
        null,
        false,
        clientUuid,
        false,
        List.of(),
        now,
        now);
  }
}
