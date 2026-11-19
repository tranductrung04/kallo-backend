package com.healthcare.kallo.controller.food;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.common.PageResponse;
import com.healthcare.kallo.config.RateLimitTestConfig;
import com.healthcare.kallo.dto.response.food.FoodResponse;
import com.healthcare.kallo.dto.response.food.FoodSearchItemResponse;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.model.food.enums.FoodSource;
import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.service.food.FoodService;
import java.math.BigDecimal;
import java.time.Instant;
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

@WebMvcTest(value = FoodController.class, properties = "spring.profiles.active=test")
@Import(RateLimitTestConfig.class)
@AutoConfigureMockMvc
class FoodControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private FoodService foodService;

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
  @DisplayName("GET /api/foods/search returns 200 with list of foods")
  void searchFoods_returnsOk() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID foodId = UUID.randomUUID();

    FoodSearchItemResponse item =
        new FoodSearchItemResponse(
            foodId,
            "Phở bò",
            FoodCategory.NOODLES_SOUP,
            "1 tô vừa",
            BigDecimal.valueOf(500.0),
            525,
            BigDecimal.valueOf(37.5),
            BigDecimal.valueOf(66.0),
            BigDecimal.valueOf(11.5),
            null,
            null,
            FoodSource.SYSTEM,
            true,
            false);

    PageResponse<FoodSearchItemResponse> pageResponse =
        new PageResponse<>(List.of(item), 0, 20, 1, 1);

    when(foodService.searchFoods(eq(userId), eq("pho"), eq(FoodCategory.NOODLES_SOUP), any()))
        .thenReturn(pageResponse);

    mockMvc
        .perform(
            get("/api/foods/search")
                .with(userJwt(userId))
                .param("q", "pho")
                .param("category", "NOODLES_SOUP"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.items[0].name").value("Phở bò"))
        .andExpect(jsonPath("$.data.items[0].caloriesPerServing").value(525));
  }

  @Test
  @DisplayName("GET /api/foods/{id} returns 200 with full food details")
  void getFoodById_returnsOk() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID foodId = UUID.randomUUID();

    FoodResponse response =
        new FoodResponse(
            foodId,
            "Bánh mì thịt",
            "banh mi thit",
            FoodCategory.BREAD_SANDWICH,
            "1 ổ",
            BigDecimal.valueOf(180.0),
            BigDecimal.valueOf(250.0),
            BigDecimal.valueOf(9.5),
            BigDecimal.valueOf(33.0),
            BigDecimal.valueOf(8.8),
            BigDecimal.valueOf(3.5),
            BigDecimal.valueOf(2.8),
            450,
            BigDecimal.valueOf(17.1),
            BigDecimal.valueOf(59.4),
            BigDecimal.valueOf(15.8),
            BigDecimal.valueOf(6.3),
            BigDecimal.valueOf(5.0),
            null,
            null,
            FoodSource.SYSTEM,
            4,
            List.of("GLUTEN"),
            List.of(MealType.BREAKFAST, MealType.LUNCH),
            25000,
            true,
            null,
            Instant.now(),
            Instant.now());

    when(foodService.getFoodById(foodId, userId)).thenReturn(response);

    mockMvc
        .perform(get("/api/foods/" + foodId).with(userJwt(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.name").value("Bánh mì thịt"))
        .andExpect(jsonPath("$.data.caloriesPerServing").value(450));
  }

  @Test
  @DisplayName("POST /api/foods/custom returns 201 Created")
  void createCustomFood_returnsCreated() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID foodId = UUID.randomUUID();

    FoodResponse response =
        new FoodResponse(
            foodId,
            "Cơm chiên nhà làm",
            "com chien nha lam",
            FoodCategory.RICE_DISH,
            "1 dĩa",
            BigDecimal.valueOf(300.0),
            BigDecimal.valueOf(180.0),
            BigDecimal.valueOf(8.0),
            BigDecimal.valueOf(28.0),
            BigDecimal.valueOf(5.0),
            BigDecimal.valueOf(1.0),
            BigDecimal.valueOf(1.0),
            540,
            BigDecimal.valueOf(24.0),
            BigDecimal.valueOf(84.0),
            BigDecimal.valueOf(15.0),
            BigDecimal.valueOf(3.0),
            BigDecimal.valueOf(3.0),
            null,
            null,
            FoodSource.USER_CUSTOM,
            null,
            List.of(),
            List.of(MealType.LUNCH),
            40000,
            false,
            userId,
            Instant.now(),
            Instant.now());

    when(foodService.createCustomFood(eq(userId), any())).thenReturn(response);

    String body =
        """
        {
          "name": "Cơm chiên nhà làm",
          "category": "RICE_DISH",
          "defaultServingName": "1 dĩa",
          "defaultServingGrams": 300.0,
          "caloriesPer100g": 180.0,
          "proteinPer100g": 8.0,
          "carbsPer100g": 28.0,
          "fatPer100g": 5.0,
          "sugarPer100g": 1.0,
          "fiberPer100g": 1.0,
          "referencePriceVnd": 40000
        }
        """;

    mockMvc
        .perform(
            post("/api/foods/custom")
                .with(userJwt(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.name").value("Cơm chiên nhà làm"))
        .andExpect(jsonPath("$.data.source").value("USER_CUSTOM"));
  }

  @Test
  @DisplayName("DELETE /api/foods/custom/{id} returns 204 No Content")
  void deleteCustomFood_returnsNoContent() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID foodId = UUID.randomUUID();

    doNothing().when(foodService).deleteCustomFood(userId, foodId);

    mockMvc
        .perform(delete("/api/foods/custom/" + foodId).with(userJwt(userId)))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("GET /api/foods/search without authentication returns 401")
  void searchFoods_unauthenticated_returns401() throws Exception {
    mockMvc.perform(get("/api/foods/search")).andExpect(status().isUnauthorized());
  }
}
